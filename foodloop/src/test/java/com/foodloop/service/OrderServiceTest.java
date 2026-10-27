package com.foodloop.service;

import com.foodloop.dto.OrderDtos.*;
import com.foodloop.entity.*;
import com.foodloop.enums.OrderStatus;
import com.foodloop.enums.Role;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ForbiddenException;
import com.foodloop.repository.CartRepository;
import com.foodloop.repository.OrderRepository;
import com.foodloop.repository.SurplusDealRepository;
import com.foodloop.repository.UserRepository;
import com.foodloop.util.PreparationEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @Mock CartRepository cartRepository;
    @Mock SurplusDealRepository dealRepository;

    OrderService service;
    User user;
    Cart cart;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, userRepository, cartRepository, dealRepository, new PreparationEstimator());
        user = user(1L, "a@b.com");
        cart = new Cart();
        cart.setUser(user);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(dealRepository.findValidDealsForFoodForUpdate(any(), any())).thenReturn(List.of());
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- placing orders ----------
    @Test
    void placeOrder_calculatesTotalAndSnapshotsOnServer() {
        addToCart(food(10L, "Burger", "100.00", 20, true), 2);

        OrderResponse r = service.placeOrder("a@b.com", new PlaceOrderRequest("12 Main Street, Aurangabad"));

        assertEquals(0, new BigDecimal("200.00").compareTo(r.totalAmount()));
        assertEquals(OrderStatus.PLACED, r.status());
        assertEquals("Burger", r.items().get(0).foodName());
        assertEquals(22, r.estimatedPrepMinutes());          // 20 + (2-1)*2 + 0 pending
        assertTrue(cart.getItems().isEmpty(), "cart must be emptied after a successful order");
    }

    @Test
    void placeOrder_writesInitialHistoryRecord() {
        addToCart(food(10L, "Burger", "100.00", 20, true), 1);
        service.placeOrder("a@b.com", new PlaceOrderRequest("12 Main Street, Aurangabad"));

        verify(orderRepository).save(org.mockito.ArgumentMatchers.argThat(o ->
                o.getHistory().size() == 1
                        && o.getHistory().get(0).getPreviousStatus() == null
                        && o.getHistory().get(0).getNewStatus() == OrderStatus.PLACED));
    }

    @Test
    void placeOrder_usesDealPriceAndCountsSoldUnits() {
        FoodItem pizza = food(11L, "Pizza", "100.00", 15, true);
        addToCart(pizza, 2);
        SurplusDeal deal = deal(5L, pizza, "70.00", 5, 0);
        when(dealRepository.findValidDealsForFoodForUpdate(any(), any())).thenReturn(List.of(deal));

        OrderResponse r = service.placeOrder("a@b.com", new PlaceOrderRequest("12 Main Street, Aurangabad"));

        assertEquals(0, new BigDecimal("140.00").compareTo(r.totalAmount()));
        assertTrue(r.items().get(0).dealApplied());
        assertEquals(2, deal.getQuantitySold());
    }

    @Test
    void placeOrder_rejectsWhenDealQuantityLimitWouldBeExceeded() {
        FoodItem pizza = food(11L, "Pizza", "100.00", 15, true);
        addToCart(pizza, 2);
        SurplusDeal deal = deal(5L, pizza, "70.00", 5, 4);      // only 1 left
        when(dealRepository.findValidDealsForFoodForUpdate(any(), any())).thenReturn(List.of(deal));

        assertThrows(ConflictException.class,
                () -> service.placeOrder("a@b.com", new PlaceOrderRequest("12 Main Street, Aurangabad")));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_rejectsEmptyCart() {
        assertThrows(BadRequestException.class,
                () -> service.placeOrder("a@b.com", new PlaceOrderRequest("12 Main Street, Aurangabad")));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_rejectsUnavailableItem() {
        addToCart(food(10L, "Burger", "100.00", 20, false), 1);
        assertThrows(BadRequestException.class,
                () -> service.placeOrder("a@b.com", new PlaceOrderRequest("12 Main Street, Aurangabad")));
        verify(orderRepository, never()).save(any());
    }

    // ---------- cancellation ----------
    @Test
    void cancel_placedOrder_setsCancelledAndWritesHistory() {
        CustomerOrder order = order(100L, user, OrderStatus.PLACED);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        OrderResponse r = service.cancel(100L, "a@b.com");

        assertEquals(OrderStatus.CANCELLED, r.status());
        assertEquals(OrderStatus.PLACED, order.getHistory().get(0).getPreviousStatus());
        assertEquals(OrderStatus.CANCELLED, order.getHistory().get(0).getNewStatus());
    }

    @Test
    void cancel_preparingOrder_isRejected() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order(100L, user, OrderStatus.PREPARING)));
        assertThrows(ConflictException.class, () -> service.cancel(100L, "a@b.com"));
    }

    @Test
    void cancel_returnsDealUnitsToTheDeal() {
        FoodItem pizza = food(11L, "Pizza", "100.00", 15, true);
        SurplusDeal deal = deal(5L, pizza, "70.00", 5, 3);
        CustomerOrder order = order(100L, user, OrderStatus.PLACED);
        OrderItem item = new OrderItem();
        item.setFoodItem(pizza);
        item.setDeal(deal);
        item.setQuantity(2);
        item.setFoodName("Pizza");
        item.setUnitPrice(new BigDecimal("70.00"));
        item.setLineTotal(new BigDecimal("140.00"));
        order.addItem(item);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(dealRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(deal));

        service.cancel(100L, "a@b.com");

        assertEquals(1, deal.getQuantitySold());
    }

    // ---------- ownership ----------
    @Test
    void getOrder_ofAnotherUser_isForbidden_butAdminMayView() {
        User other = user(2L, "other@b.com");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order(100L, other, OrderStatus.PLACED)));

        assertThrows(ForbiddenException.class, () -> service.getOrder(100L, "a@b.com", false));
        assertEquals(100L, service.getOrder(100L, "admin@b.com", true).id());
    }

    @Test
    void cancel_ofAnotherUsersOrder_isForbidden() {
        User other = user(2L, "other@b.com");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order(100L, other, OrderStatus.PLACED)));
        assertThrows(ForbiddenException.class, () -> service.cancel(100L, "a@b.com"));
    }

    // ---------- admin status changes ----------
    @Test
    void updateStatus_validStep_addsTimelineEntry() {
        CustomerOrder order = order(100L, user, OrderStatus.PLACED);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        OrderResponse r = service.updateStatus(100L, "admin@b.com", new UpdateStatusRequest(OrderStatus.CONFIRMED, null));

        assertEquals(OrderStatus.CONFIRMED, r.status());
        assertEquals(1, order.getHistory().size());
        assertEquals("admin@b.com", order.getHistory().get(0).getChangedBy());
    }

    @Test
    void updateStatus_invalidJump_isRejectedAndNothingIsRecorded() {
        CustomerOrder order = order(100L, user, OrderStatus.PLACED);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(ConflictException.class,
                () -> service.updateStatus(100L, "admin@b.com", new UpdateStatusRequest(OrderStatus.DELIVERED, null)));
        assertEquals(OrderStatus.PLACED, order.getStatus());
        assertTrue(order.getHistory().isEmpty());
    }

    @Test
    void estimate_isLabelledAsAnEstimate() {
        CustomerOrder order = order(100L, user, OrderStatus.PLACED);
        order.setEstimatedPrepMinutes(25);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        EstimateResponse e = service.estimate(100L, "a@b.com", false);

        assertEquals(25, e.estimatedPrepMinutes());
        assertTrue(e.note().contains("ESTIMATE"));
    }

    // ---------- test data helpers ----------
    private void addToCart(FoodItem food, int qty) {
        CartItem ci = new CartItem();
        ci.setCart(cart);
        ci.setFoodItem(food);
        ci.setQuantity(qty);
        cart.getItems().add(ci);
    }

    private static User user(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setName("Test " + id);
        u.setEmail(email);
        u.setRole(Role.USER);
        return u;
    }

    private static FoodItem food(Long id, String name, String price, int prep, boolean available) {
        Category c = new Category();
        c.setId(1L);
        c.setName("Main");
        FoodItem f = new FoodItem();
        f.setId(id);
        f.setName(name);
        f.setPrice(new BigDecimal(price));
        f.setPrepTimeMinutes(prep);
        f.setAvailable(available);
        f.setCategory(c);
        return f;
    }

    private static SurplusDeal deal(Long id, FoodItem food, String price, Integer limit, int sold) {
        SurplusDeal d = new SurplusDeal();
        d.setId(id);
        d.setFoodItem(food);
        d.setOriginalPrice(food.getPrice());
        d.setDiscountedPrice(new BigDecimal(price));
        d.setQuantityLimit(limit);
        d.setQuantitySold(sold);
        return d;
    }

    private static CustomerOrder order(Long id, User owner, OrderStatus status) {
        CustomerOrder o = new CustomerOrder();
        o.setId(id);
        o.setUser(owner);
        o.setStatus(status);
        o.setDeliveryAddress("12 Main Street, Aurangabad");
        return o;
    }
}

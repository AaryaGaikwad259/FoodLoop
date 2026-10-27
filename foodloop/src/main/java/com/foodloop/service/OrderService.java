package com.foodloop.service;

import com.foodloop.dto.OrderDtos.*;
import com.foodloop.entity.*;
import com.foodloop.enums.OrderStatus;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ForbiddenException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.CartRepository;
import com.foodloop.repository.OrderRepository;
import com.foodloop.repository.SurplusDealRepository;
import com.foodloop.repository.UserRepository;
import com.foodloop.util.Mapper;
import com.foodloop.util.PreparationEstimator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final SurplusDealRepository dealRepository;
    private final PreparationEstimator estimator;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository, CartRepository cartRepository,
                        SurplusDealRepository dealRepository, PreparationEstimator estimator) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.dealRepository = dealRepository;
        this.estimator = estimator;
    }

    // =====================================================================
    //  PLACE ORDER  (one database transaction: all steps succeed or none do)
    // =====================================================================
    @Transactional
    public OrderResponse placeOrder(String email, PlaceOrderRequest request) {
        User user = findUser(email);
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BadRequestException("Your cart is empty"));
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }

        LocalDateTime now = LocalDateTime.now();
        CustomerOrder order = new CustomerOrder();
        order.setUser(user);
        order.setDeliveryAddress(request.deliveryAddress().trim());
        order.setOrderDate(now);
        order.setStatus(OrderStatus.PLACED);

        BigDecimal total = BigDecimal.ZERO;
        int totalUnits = 0;
        int longestPrep = 0;

        for (CartItem ci : cart.getItems()) {
            FoodItem food = ci.getFoodItem();                       // current data loaded from the database
            int qty = ci.getQuantity();
            if (!food.isAvailable()) {
                throw new BadRequestException("'" + food.getName() + "' is no longer available. Remove it from your cart.");
            }

            // Look for a valid surplus deal and LOCK its row so concurrent orders cannot oversell it.
            SurplusDeal deal = dealRepository.findValidDealsForFoodForUpdate(food.getId(), now)
                    .stream().findFirst().orElse(null);
            BigDecimal unitPrice = food.getPrice();
            if (deal != null) {
                Integer remaining = deal.getRemainingQuantity();
                if (remaining != null && qty > remaining) {
                    throw new ConflictException("Only " + remaining + " unit(s) of '" + food.getName()
                            + "' are left at the deal price. Reduce the quantity in your cart.");
                }
                unitPrice = deal.getDiscountedPrice();
                deal.setQuantitySold(deal.getQuantitySold() + qty);
            }

            OrderItem item = new OrderItem();
            item.setFoodItem(food);
            item.setDeal(deal);
            item.setFoodName(food.getName());                       // snapshot
            item.setUnitPrice(unitPrice);                           // snapshot
            item.setQuantity(qty);
            item.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(qty)));
            order.addItem(item);

            total = total.add(item.getLineTotal());
            totalUnits += qty;
            longestPrep = Math.max(longestPrep, food.getPrepTimeMinutes());
        }

        long pendingAhead = orderRepository.countByStatusIn(
                Arrays.stream(OrderStatus.values()).filter(OrderStatus::isPendingWorkload).toList());
        order.setTotalAmount(total);
        order.setEstimatedPrepMinutes(estimator.estimate(longestPrep, totalUnits, pendingAhead));
        order.addHistory(null, OrderStatus.PLACED, user.getEmail(), "Order placed by customer");

        CustomerOrder saved = orderRepository.save(order);
        cart.getItems().clear();                                    // cart is emptied only now, inside the same transaction
        cartRepository.save(cart);
        return Mapper.toOrder(saved);
    }

    // =====================================================================
    //  READ
    // =====================================================================
    @Transactional(readOnly = true)
    public List<OrderResponse> myOrders(String email) {
        User user = findUser(email);
        return orderRepository.findByUserIdOrderByOrderDateDesc(user.getId()).stream().map(Mapper::toOrder).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> allOrders(OrderStatus status) {
        List<CustomerOrder> orders = status == null ? orderRepository.findAllByOrderByOrderDateDesc()
                                                    : orderRepository.findByStatusOrderByOrderDateDesc(status);
        return orders.stream().map(Mapper::toOrder).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id, String email, boolean isAdmin) {
        return Mapper.toOrder(loadAuthorized(id, email, isAdmin));
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(Long id, String email, boolean isAdmin) {
        return loadAuthorized(id, email, isAdmin).getHistory().stream().map(Mapper::toTimeline).toList();
    }

    @Transactional(readOnly = true)
    public EstimateResponse estimate(Long id, String email, boolean isAdmin) {
        CustomerOrder o = loadAuthorized(id, email, isAdmin);
        String note = "This is an ESTIMATE of kitchen preparation time only, calculated when the order was placed. "
                + "It is not a guaranteed delivery time.";
        return new EstimateResponse(o.getId(), o.getStatus(), o.getEstimatedPrepMinutes(),
                o.getOrderDate().plusMinutes(o.getEstimatedPrepMinutes()), note);
    }

    // =====================================================================
    //  STATUS CHANGES
    // =====================================================================
    @Transactional
    public OrderResponse cancel(Long id, String email) {
        CustomerOrder order = loadAuthorized(id, email, false);     // customers can only cancel their OWN orders
        if (!order.getStatus().isCustomerCancellable()) {
            throw new ConflictException("Order cannot be cancelled when its status is " + order.getStatus());
        }
        changeStatus(order, OrderStatus.CANCELLED, email, "Cancelled by customer");
        return Mapper.toOrder(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse updateStatus(Long id, String adminEmail, UpdateStatusRequest request) {
        CustomerOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id " + id));
        OrderStatus next = request.status();
        if (!order.getStatus().canTransitionTo(next)) {
            throw new ConflictException("Invalid status change: " + order.getStatus() + " -> " + next
                    + ". Allowed next statuses: " + order.getStatus().allowedNext());
        }
        String note = (request.note() == null || request.note().isBlank())
                ? "Status changed by admin" : request.note().trim();
        changeStatus(order, next, adminEmail, note);
        return Mapper.toOrder(orderRepository.save(order));
    }

    /** The ONLY place that changes an order's status, so a history record is always written. */
    private void changeStatus(CustomerOrder order, OrderStatus next, String actor, String description) {
        OrderStatus previous = order.getStatus();
        order.setStatus(next);
        order.addHistory(previous, next, actor, description);
        if (next == OrderStatus.CANCELLED) {
            releaseDealUnits(order);
        }
    }

    /** When an order is cancelled, give the limited deal units back so other customers can use them. */
    private void releaseDealUnits(CustomerOrder order) {
        for (OrderItem item : order.getItems()) {
            if (item.getDeal() != null) {
                dealRepository.findByIdForUpdate(item.getDeal().getId()).ifPresent(d ->
                        d.setQuantitySold(Math.max(0, d.getQuantitySold() - item.getQuantity())));
            }
        }
    }

    // =====================================================================
    //  helpers
    // =====================================================================
    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** Loads an order and enforces ownership: a customer can only touch their own orders; admins can see all. */
    private CustomerOrder loadAuthorized(Long id, String email, boolean isAdmin) {
        CustomerOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id " + id));
        if (!isAdmin && !order.getUser().getEmail().equals(email)) {
            throw new ForbiddenException("You do not have access to this order");
        }
        return order;
    }
}

package com.foodloop.service;

import com.foodloop.dto.CartDtos.*;
import com.foodloop.entity.*;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.CartRepository;
import com.foodloop.repository.FoodItemRepository;
import com.foodloop.repository.SurplusDealRepository;
import com.foodloop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock UserRepository userRepository;
    @Mock FoodItemRepository foodItemRepository;
    @Mock SurplusDealRepository dealRepository;

    CartService service;
    Cart cart;

    @BeforeEach
    void setUp() {
        service = new CartService(cartRepository, userRepository, foodItemRepository, dealRepository);
        User user = new User();
        user.setId(1L);
        user.setEmail("a@b.com");
        cart = new Cart();
        cart.setUser(user);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void addingSameFoodTwice_updatesQuantityInsteadOfDuplicating() {
        when(foodItemRepository.findById(10L)).thenReturn(Optional.of(food(10L, "50.00", true)));

        service.addItem("a@b.com", new AddCartItemRequest(10L, 2));
        CartResponse r = service.addItem("a@b.com", new AddCartItemRequest(10L, 3));

        assertEquals(1, r.items().size());
        assertEquals(5, r.items().get(0).quantity());
        assertEquals(0, new BigDecimal("250.00").compareTo(r.totalAmount()));   // server-side total: 5 x 50
    }

    @Test
    void unavailableItemCannotBeAdded() {
        when(foodItemRepository.findById(10L)).thenReturn(Optional.of(food(10L, "50.00", false)));
        assertThrows(BadRequestException.class, () -> service.addItem("a@b.com", new AddCartItemRequest(10L, 1)));
        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    void unknownFoodGivesNotFound() {
        when(foodItemRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.addItem("a@b.com", new AddCartItemRequest(99L, 1)));
    }

    @Test
    void quantityAboveMaximumIsRejected() {
        when(foodItemRepository.findById(10L)).thenReturn(Optional.of(food(10L, "50.00", true)));
        service.addItem("a@b.com", new AddCartItemRequest(10L, 40));
        assertThrows(BadRequestException.class, () -> service.addItem("a@b.com", new AddCartItemRequest(10L, 20)));
    }

    @Test
    void cannotTouchAnItemThatIsNotInYourOwnCart() {
        assertThrows(ResourceNotFoundException.class, () -> service.removeItem("a@b.com", 12345L));
    }

    private static FoodItem food(Long id, String price, boolean available) {
        Category c = new Category();
        c.setId(1L);
        c.setName("Main");
        FoodItem f = new FoodItem();
        f.setId(id);
        f.setName("Item " + id);
        f.setPrice(new BigDecimal(price));
        f.setAvailable(available);
        f.setPrepTimeMinutes(10);
        f.setCategory(c);
        return f;
    }
}

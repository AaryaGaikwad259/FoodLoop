package com.foodloop.service;

import com.foodloop.dto.CartDtos.*;
import com.foodloop.entity.Cart;
import com.foodloop.entity.CartItem;
import com.foodloop.entity.FoodItem;
import com.foodloop.entity.SurplusDeal;
import com.foodloop.entity.User;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.CartRepository;
import com.foodloop.repository.FoodItemRepository;
import com.foodloop.repository.SurplusDealRepository;
import com.foodloop.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CartService {

    static final int MAX_QUANTITY_PER_ITEM = 50;

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final FoodItemRepository foodItemRepository;
    private final SurplusDealRepository dealRepository;

    public CartService(CartRepository cartRepository, UserRepository userRepository,
                       FoodItemRepository foodItemRepository, SurplusDealRepository dealRepository) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.foodItemRepository = foodItemRepository;
        this.dealRepository = dealRepository;
    }

    @Transactional
    public CartResponse getCart(String email) {
        return toResponse(getOrCreateCart(email));
    }

    @Transactional
    public CartResponse addItem(String email, AddCartItemRequest request) {
        Cart cart = getOrCreateCart(email);
        FoodItem food = foodItemRepository.findById(request.foodItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Food item not found with id " + request.foodItemId()));
        if (!food.isAvailable()) {
            throw new BadRequestException("'" + food.getName() + "' is currently unavailable");
        }
        // Same food added again -> increase the quantity instead of creating a duplicate row
        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getFoodItem().getId().equals(food.getId()))
                .findFirst().orElse(null);
        if (existing != null) {
            int newQty = existing.getQuantity() + request.quantity();
            checkMax(newQty);
            existing.setQuantity(newQty);
        } else {
            checkMax(request.quantity());
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setFoodItem(food);
            item.setQuantity(request.quantity());
            cart.getItems().add(item);
        }
        return toResponse(cartRepository.saveAndFlush(cart));
    }

    @Transactional
    public CartResponse updateQuantity(String email, Long itemId, UpdateQuantityRequest request) {
        Cart cart = getOrCreateCart(email);
        CartItem item = findItem(cart, itemId);          // searches only THIS user's cart -> ownership is guaranteed
        if (!item.getFoodItem().isAvailable()) {
            throw new BadRequestException("'" + item.getFoodItem().getName() + "' is currently unavailable");
        }
        checkMax(request.quantity());
        item.setQuantity(request.quantity());
        return toResponse(cartRepository.saveAndFlush(cart));
    }

    @Transactional
    public CartResponse removeItem(String email, Long itemId) {
        Cart cart = getOrCreateCart(email);
        CartItem item = findItem(cart, itemId);
        cart.getItems().remove(item);                    // orphanRemoval deletes the row
        return toResponse(cartRepository.saveAndFlush(cart));
    }

    @Transactional
    public void clear(String email) {
        Cart cart = getOrCreateCart(email);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    // ---------------- helpers ----------------
    private Cart getOrCreateCart(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart c = new Cart();
            c.setUser(user);
            return cartRepository.save(c);
        });
    }

    private CartItem findItem(Cart cart, Long itemId) {
        return cart.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id " + itemId));
    }

    private void checkMax(int qty) {
        if (qty > MAX_QUANTITY_PER_ITEM) {
            throw new BadRequestException("You can order at most " + MAX_QUANTITY_PER_ITEM + " units of one item");
        }
    }

    /** Prices are calculated here on the server (deal price if a valid deal exists, otherwise the menu price). */
    private CartResponse toResponse(Cart cart) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal total = BigDecimal.ZERO;
        int units = 0;
        List<CartItemResponse> items = new java.util.ArrayList<>();
        for (CartItem ci : cart.getItems()) {
            FoodItem food = ci.getFoodItem();
            SurplusDeal deal = dealRepository.findValidDealsForFood(food.getId(), now).stream().findFirst().orElse(null);
            BigDecimal unit = deal != null ? deal.getDiscountedPrice() : food.getPrice();
            BigDecimal line = unit.multiply(BigDecimal.valueOf(ci.getQuantity()));
            items.add(new CartItemResponse(ci.getId(), food.getId(), food.getName(), food.getPrice(), unit,
                    deal != null, ci.getQuantity(), line));
            total = total.add(line);
            units += ci.getQuantity();
        }
        return new CartResponse(cart.getId(), items, units, total);
    }
}

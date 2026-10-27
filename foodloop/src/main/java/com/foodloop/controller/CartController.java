package com.foodloop.controller;

import com.foodloop.dto.CartDtos.*;
import com.foodloop.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse get(Authentication auth) {
        return cartService.getCart(auth.getName());
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> add(Authentication auth, @Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addItem(auth.getName(), request));
    }

    @PatchMapping("/items/{itemId}")
    public CartResponse updateQuantity(Authentication auth, @PathVariable Long itemId,
                                       @Valid @RequestBody UpdateQuantityRequest request) {
        return cartService.updateQuantity(auth.getName(), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse remove(Authentication auth, @PathVariable Long itemId) {
        return cartService.removeItem(auth.getName(), itemId);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(Authentication auth) {
        cartService.clear(auth.getName());
        return ResponseEntity.noContent().build();
    }
}

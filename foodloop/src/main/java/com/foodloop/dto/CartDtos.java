package com.foodloop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {
    private CartDtos() {}

    // The client sends only WHICH food and HOW MANY. The price is always read from the database.
    public record AddCartItemRequest(
            @NotNull(message = "foodItemId is required") Long foodItemId,
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 50, message = "Quantity must be at most 50") Integer quantity) {}

    public record UpdateQuantityRequest(
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 50, message = "Quantity must be at most 50") Integer quantity) {}

    public record CartItemResponse(Long id, Long foodItemId, String foodName, BigDecimal regularPrice,
                                   BigDecimal unitPrice, boolean dealApplied, int quantity, BigDecimal lineTotal) {}

    public record CartResponse(Long id, List<CartItemResponse> items, int totalUnits, BigDecimal totalAmount) {}
}

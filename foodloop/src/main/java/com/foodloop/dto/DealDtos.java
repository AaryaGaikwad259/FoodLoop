package com.foodloop.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class DealDtos {
    private DealDtos() {}

    // Send EITHER discountPercent OR discountedPrice (not both). The server calculates the other one.
    public record DealRequest(
            @NotNull(message = "foodItemId is required") Long foodItemId,
            @DecimalMin(value = "0.01", message = "Discounted price must be greater than 0") BigDecimal discountedPrice,
            @DecimalMin(value = "1", message = "Discount percent must be at least 1")
            @DecimalMax(value = "99", message = "Discount percent must be at most 99") BigDecimal discountPercent,
            @NotNull(message = "startTime is required") LocalDateTime startTime,
            @NotNull(message = "endTime is required") LocalDateTime endTime,
            @Min(value = 1, message = "Quantity limit must be at least 1") Integer quantityLimit,
            Boolean active) {}

    public record DealStatusRequest(@NotNull(message = "active is required") Boolean active) {}

    public record DealResponse(Long id, Long foodItemId, String foodName, BigDecimal originalPrice,
                               BigDecimal discountedPrice, BigDecimal discountPercent, LocalDateTime startTime,
                               LocalDateTime endTime, Integer quantityLimit, int quantitySold,
                               Integer remainingQuantity, boolean active, boolean currentlyValid) {}
}

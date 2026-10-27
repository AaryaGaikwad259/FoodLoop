package com.foodloop.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record CategoryRequest(
            @NotBlank(message = "Category name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name,
            @Size(max = 500, message = "Description must be at most 500 characters") String description,
            Boolean active) {}

    public record CategoryResponse(Long id, String name, String description, boolean active) {}

    public record FoodRequest(
            @NotBlank(message = "Food name is required") @Size(max = 150, message = "Name must be at most 150 characters") String name,
            @Size(max = 1000, message = "Description must be at most 1000 characters") String description,
            @NotNull(message = "Price is required")
            @DecimalMin(value = "0.01", message = "Price must be greater than 0")
            @Digits(integer = 8, fraction = 2, message = "Price may have at most 2 decimal places") BigDecimal price,
            @Size(max = 500, message = "Image URL must be at most 500 characters") String imageUrl,
            Boolean available,
            @NotNull(message = "Preparation time is required")
            @Min(value = 1, message = "Preparation time must be at least 1 minute")
            @Max(value = 240, message = "Preparation time must be at most 240 minutes") Integer prepTimeMinutes,
            @NotNull(message = "Category id is required") Long categoryId) {}

    public record AvailabilityRequest(@NotNull(message = "available is required") Boolean available) {}

    public record FoodResponse(Long id, String name, String description, BigDecimal price, String imageUrl,
                               boolean available, int prepTimeMinutes, Long categoryId, String categoryName) {}
}

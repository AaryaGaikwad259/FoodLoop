package com.foodloop.util;

import com.foodloop.dto.AuthDtos.UserResponse;
import com.foodloop.dto.CartDtos.CartItemResponse;
import com.foodloop.dto.CatalogDtos.CategoryResponse;
import com.foodloop.dto.CatalogDtos.FoodResponse;
import com.foodloop.dto.DealDtos.DealResponse;
import com.foodloop.dto.OrderDtos.OrderItemResponse;
import com.foodloop.dto.OrderDtos.OrderResponse;
import com.foodloop.dto.OrderDtos.TimelineEntry;
import com.foodloop.entity.*;

import java.time.LocalDateTime;
import java.util.List;

/** Converts entities to response DTOs. Entities are never returned directly (avoids recursion and leaking fields). */
public final class Mapper {
    private Mapper() {}

    public static UserResponse toUser(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getAddress(), u.getRole().name());
    }

    public static CategoryResponse toCategory(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive());
    }

    public static FoodResponse toFood(FoodItem f) {
        return new FoodResponse(f.getId(), f.getName(), f.getDescription(), f.getPrice(), f.getImageUrl(),
                f.isAvailable(), f.getPrepTimeMinutes(), f.getCategory().getId(), f.getCategory().getName());
    }

    public static DealResponse toDeal(SurplusDeal d) {
        return new DealResponse(d.getId(), d.getFoodItem().getId(), d.getFoodItem().getName(), d.getOriginalPrice(),
                d.getDiscountedPrice(), d.getDiscountPercent(), d.getStartTime(), d.getEndTime(), d.getQuantityLimit(),
                d.getQuantitySold(), d.getRemainingQuantity(), d.isActive(), d.isCurrentlyValid(LocalDateTime.now()));
    }

    public static OrderResponse toOrder(CustomerOrder o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> new OrderItemResponse(i.getId(), i.getFoodItem() == null ? null : i.getFoodItem().getId(),
                        i.getFoodName(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal(), i.getDeal() != null))
                .toList();
        return new OrderResponse(o.getId(), o.getStatus(), o.getTotalAmount(), o.getDeliveryAddress(), o.getOrderDate(),
                o.getEstimatedPrepMinutes(), o.getUser().getName(), o.getUser().getEmail(), items);
    }

    public static TimelineEntry toTimeline(OrderStatusHistory h) {
        return new TimelineEntry(h.getPreviousStatus(), h.getNewStatus(), h.getChangedAt(), h.getChangedBy(), h.getDescription());
    }
}

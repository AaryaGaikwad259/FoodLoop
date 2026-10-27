package com.foodloop.dto;

import com.foodloop.enums.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    public record PlaceOrderRequest(
            @NotBlank(message = "Delivery address is required")
            @Size(min = 10, max = 255, message = "Delivery address must be 10-255 characters") String deliveryAddress) {}

    public record UpdateStatusRequest(
            @NotNull(message = "status is required") OrderStatus status,
            @Size(max = 200, message = "Note must be at most 200 characters") String note) {}

    public record OrderItemResponse(Long id, Long foodItemId, String foodName, BigDecimal unitPrice, int quantity,
                                    BigDecimal lineTotal, boolean dealApplied) {}

    public record OrderResponse(Long id, OrderStatus status, BigDecimal totalAmount, String deliveryAddress,
                                LocalDateTime orderDate, int estimatedPrepMinutes, String customerName,
                                String customerEmail, List<OrderItemResponse> items) {}

    public record TimelineEntry(OrderStatus previousStatus, OrderStatus newStatus, LocalDateTime changedAt,
                                String changedBy, String description) {}

    public record EstimateResponse(Long orderId, OrderStatus status, int estimatedPrepMinutes,
                                   LocalDateTime estimatedReadyAt, String note) {}
}

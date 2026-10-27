package com.foodloop.enums;

import java.util.Set;

/**
 * Order lifecycle:
 * PLACED -> CONFIRMED -> PREPARING -> READY -> OUT_FOR_DELIVERY -> DELIVERED
 * Cancellation is allowed only before the kitchen has finished (PLACED, CONFIRMED, PREPARING).
 */
public enum OrderStatus {
    PLACED, CONFIRMED, PREPARING, READY, OUT_FOR_DELIVERY, DELIVERED, CANCELLED;

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PLACED -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> Set.of(PREPARING, CANCELLED);
            case PREPARING -> Set.of(READY, CANCELLED);
            case READY -> Set.of(OUT_FOR_DELIVERY);
            case OUT_FOR_DELIVERY -> Set.of(DELIVERED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    /** A customer may cancel only while the order has not started being prepared. */
    public boolean isCustomerCancellable() {
        return this == PLACED || this == CONFIRMED;
    }

    /** Orders that are still "in the kitchen queue" - used by the preparation-time estimate. */
    public boolean isPendingWorkload() {
        return this == PLACED || this == CONFIRMED || this == PREPARING;
    }
}

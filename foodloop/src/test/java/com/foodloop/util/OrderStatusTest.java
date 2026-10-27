package com.foodloop.util;

import com.foodloop.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTest {

    @Test
    void happyPathIsAllowedStepByStep() {
        assertTrue(OrderStatus.PLACED.canTransitionTo(OrderStatus.CONFIRMED));
        assertTrue(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PREPARING));
        assertTrue(OrderStatus.PREPARING.canTransitionTo(OrderStatus.READY));
        assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY));
        assertTrue(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERED));
    }

    @Test
    void skippingOrGoingBackwardsIsRejected() {
        assertFalse(OrderStatus.PLACED.canTransitionTo(OrderStatus.DELIVERED));
        assertFalse(OrderStatus.PREPARING.canTransitionTo(OrderStatus.CONFIRMED));
    }

    @Test
    void finalStatesHaveNoNextStatus() {
        assertTrue(OrderStatus.DELIVERED.allowedNext().isEmpty());
        assertTrue(OrderStatus.CANCELLED.allowedNext().isEmpty());
    }

    @Test
    void cancellationRules() {
        assertTrue(OrderStatus.PLACED.isCustomerCancellable());
        assertTrue(OrderStatus.CONFIRMED.isCustomerCancellable());
        assertFalse(OrderStatus.PREPARING.isCustomerCancellable());
        assertFalse(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.CANCELLED));
        assertTrue(OrderStatus.PREPARING.canTransitionTo(OrderStatus.CANCELLED));   // admin only
    }
}

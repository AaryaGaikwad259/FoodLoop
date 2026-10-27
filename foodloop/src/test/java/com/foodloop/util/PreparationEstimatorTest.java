package com.foodloop.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreparationEstimatorTest {

    private final PreparationEstimator estimator = new PreparationEstimator();

    @Test
    void singleUnitEmptyKitchen_usesOnlyLongestPrepTime() {
        assertEquals(15, estimator.estimate(15, 1, 0));
    }

    @Test
    void extraUnitsAndQueueAddTwoMinutesEach() {
        // 20 + (3-1)*2 + 5*2 = 34
        assertEquals(34, estimator.estimate(20, 3, 5));
    }

    @Test
    void delaysAreCapped() {
        // unit delay capped at 30, queue delay capped at 30
        assertEquals(10 + 30 + 30, estimator.estimate(10, 100, 100));
    }
}

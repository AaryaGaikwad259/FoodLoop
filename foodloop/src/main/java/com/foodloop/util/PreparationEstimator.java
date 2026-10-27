package com.foodloop.util;

import org.springframework.stereotype.Component;

/**
 * Preparation-time ESTIMATE (not a guaranteed delivery time).
 *
 * estimate = longest item prep time                  (items are cooked in parallel)
 *          + 2 minutes for every extra unit          (capped at 30)  -> bigger orders take longer
 *          + 2 minutes for every pending order ahead (capped at 30)  -> busy kitchen = longer wait
 *
 * "Pending" orders are those in PLACED, CONFIRMED or PREPARING status when the order is placed.
 */
@Component
public class PreparationEstimator {

    public static final int MINUTES_PER_EXTRA_UNIT = 2;
    public static final int MINUTES_PER_PENDING_ORDER = 2;
    public static final int MAX_EXTRA = 30;

    public int estimate(int longestPrepMinutes, int totalUnits, long pendingOrdersAhead) {
        int extraUnits = Math.max(0, totalUnits - 1);
        int unitDelay = (int) Math.min((long) extraUnits * MINUTES_PER_EXTRA_UNIT, MAX_EXTRA);
        int queueDelay = (int) Math.min(pendingOrdersAhead * MINUTES_PER_PENDING_ORDER, MAX_EXTRA);
        return longestPrepMinutes + unitDelay + queueDelay;
    }
}

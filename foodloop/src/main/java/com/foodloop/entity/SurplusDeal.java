package com.foodloop.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "surplus_deals", indexes = @Index(name = "idx_deal_food_active", columnList = "food_item_id, active"))
@Getter @Setter @NoArgsConstructor
public class SurplusDeal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItem foodItem;

    @Column(name = "original_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "discounted_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountedPrice;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    // null = unlimited
    @Column(name = "quantity_limit")
    private Integer quantityLimit;

    @Column(name = "quantity_sold", nullable = false)
    private int quantitySold = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Valid = switched on, inside its time window, and not sold out. Checked on every read/purchase (no scheduler needed). */
    public boolean isCurrentlyValid(LocalDateTime now) {
        return active && !startTime.isAfter(now) && endTime.isAfter(now)
                && (quantityLimit == null || quantitySold < quantityLimit);
    }

    public Integer getRemainingQuantity() {
        return quantityLimit == null ? null : Math.max(0, quantityLimit - quantitySold);
    }
}

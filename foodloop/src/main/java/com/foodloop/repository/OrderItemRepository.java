package com.foodloop.repository;

import com.foodloop.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    boolean existsByFoodItemId(Long foodItemId);
    boolean existsByDealId(Long dealId);
}

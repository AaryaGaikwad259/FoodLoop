package com.foodloop.repository;

import com.foodloop.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    void deleteByFoodItemId(Long foodItemId);
}

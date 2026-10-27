package com.foodloop.repository;

import com.foodloop.entity.CustomerOrder;
import com.foodloop.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findByUserIdOrderByOrderDateDesc(Long userId);
    List<CustomerOrder> findAllByOrderByOrderDateDesc();
    List<CustomerOrder> findByStatusOrderByOrderDateDesc(OrderStatus status);
    long countByStatusIn(Collection<OrderStatus> statuses);
}

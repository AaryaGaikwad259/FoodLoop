package com.foodloop.controller;

import com.foodloop.dto.OrderDtos.OrderResponse;
import com.foodloop.dto.OrderDtos.UpdateStatusRequest;
import com.foodloop.enums.OrderStatus;
import com.foodloop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Everything under /api/admin/** requires the ADMIN role (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** GET /api/admin/orders?status=PLACED */
    @GetMapping
    public List<OrderResponse> all(@RequestParam(required = false) OrderStatus status) {
        return orderService.allOrders(status);
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable Long id, Authentication auth,
                                      @Valid @RequestBody UpdateStatusRequest request) {
        return orderService.updateStatus(id, auth.getName(), request);
    }
}

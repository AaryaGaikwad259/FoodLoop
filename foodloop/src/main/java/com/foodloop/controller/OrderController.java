package com.foodloop.controller;
import com.foodloop.enums.OrderStatus;

import com.foodloop.dto.OrderDtos.*;
import com.foodloop.service.OrderService;
import com.foodloop.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(Authentication auth, @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(auth.getName(), request));
    }

    @GetMapping("/my")
    public List<OrderResponse> myOrders(Authentication auth) {
        return orderService.myOrders(auth.getName());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id, Authentication auth) {
        return orderService.getOrder(id, auth.getName(), SecurityUtil.isAdmin(auth));
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id, Authentication auth) {
        return orderService.cancel(id, auth.getName());
    }

    @GetMapping("/{id}/timeline")
    public List<TimelineEntry> timeline(@PathVariable Long id, Authentication auth) {
        return orderService.timeline(id, auth.getName(), SecurityUtil.isAdmin(auth));
    }

    @GetMapping("/{id}/estimate")
    public EstimateResponse estimate(@PathVariable Long id, Authentication auth) {
        return orderService.estimate(id, auth.getName(), SecurityUtil.isAdmin(auth));
    }
    @GetMapping("/admin/all")
public List<OrderResponse> allOrders(
        @RequestParam(required = false) OrderStatus status) {
    return orderService.allOrders(status);
}

@PatchMapping("/admin/{id}/status")
public OrderResponse updateStatus(
        @PathVariable Long id,
        @RequestBody @Valid UpdateStatusRequest request,
        Authentication auth) {
    if (!SecurityUtil.isAdmin(auth)) {
        throw new org.springframework.security.access.AccessDeniedException("Admin access required");
    }
    return orderService.updateStatus(id, auth.getName(), request);
}
}

package com.foodloop.controller;

import com.foodloop.dto.DealDtos.DealRequest;
import com.foodloop.dto.DealDtos.DealResponse;
import com.foodloop.dto.DealDtos.DealStatusRequest;
import com.foodloop.service.SurplusDealService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/surplus-deals")
public class AdminSurplusDealController {

    private final SurplusDealService dealService;

    public AdminSurplusDealController(SurplusDealService dealService) {
        this.dealService = dealService;
    }

    /** All deals (including expired/inactive) for the admin dashboard. */
    @GetMapping
    public List<DealResponse> all() {
        return dealService.listAll();
    }

    @PostMapping
    public ResponseEntity<DealResponse> create(@Valid @RequestBody DealRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dealService.create(request));
    }

    @PutMapping("/{id}")
    public DealResponse update(@PathVariable Long id, @Valid @RequestBody DealRequest request) {
        return dealService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public DealResponse setStatus(@PathVariable Long id, @Valid @RequestBody DealStatusRequest request) {
        return dealService.setActive(id, request.active());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        dealService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

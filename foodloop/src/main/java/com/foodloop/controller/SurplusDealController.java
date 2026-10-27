package com.foodloop.controller;

import com.foodloop.dto.DealDtos.DealResponse;
import com.foodloop.service.SurplusDealService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public (customer-facing) deal endpoints. */
@RestController
@RequestMapping("/api/surplus-deals")
public class SurplusDealController {

    private final SurplusDealService dealService;

    public SurplusDealController(SurplusDealService dealService) {
        this.dealService = dealService;
    }

    @GetMapping
    public List<DealResponse> currentDeals() {
        return dealService.listCurrentlyValid();
    }

    @GetMapping("/{id}")
    public DealResponse get(@PathVariable Long id) {
        return dealService.get(id);
    }
}

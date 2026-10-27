package com.foodloop.controller;

import com.foodloop.dto.CatalogDtos.AvailabilityRequest;
import com.foodloop.dto.CatalogDtos.FoodRequest;
import com.foodloop.dto.CatalogDtos.FoodResponse;
import com.foodloop.service.FoodService;
import com.foodloop.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
    }

    /** GET /api/foods?name=pizza&categoryId=2&available=true */
    @GetMapping
    public List<FoodResponse> search(@RequestParam(required = false) String name,
                                     @RequestParam(required = false) Long categoryId,
                                     @RequestParam(required = false) Boolean available,
                                     Authentication auth) {
        return foodService.search(name, categoryId, available, SecurityUtil.isAdmin(auth));
    }

    @GetMapping("/{id}")
    public FoodResponse get(@PathVariable Long id, Authentication auth) {
        return foodService.get(id, SecurityUtil.isAdmin(auth));
    }

    @PostMapping
    public ResponseEntity<FoodResponse> create(@Valid @RequestBody FoodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(foodService.create(request));
    }

    @PutMapping("/{id}")
    public FoodResponse update(@PathVariable Long id, @Valid @RequestBody FoodRequest request) {
        return foodService.update(id, request);
    }

    @PatchMapping("/{id}/availability")
    public FoodResponse setAvailability(@PathVariable Long id, @Valid @RequestBody AvailabilityRequest request) {
        return foodService.setAvailability(id, request.available());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        foodService.delete(id);
        return ResponseEntity.noContent().build();
    }
}


package com.foodloop.service;

import com.foodloop.dto.CatalogDtos.FoodResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationService {

    private final FoodService foodService;
    private final RestClient restClient;

    public RecommendationService(
            FoodService foodService,
            @Value("${ml.recommendation.url}") String mlUrl) {
        this.foodService = foodService;
        this.restClient = RestClient.builder()
                .baseUrl(mlUrl)
                .build();
    }

    public List<Map<String, Object>> recommend(
            Long itemId, String preference, int limit) {

        List<FoodResponse> foods =
                foodService.search(null, null, true, false);
                if (foods.isEmpty()) {
                return List.of();
                   }
        List<Map<String, Object>> items = foods.stream()
                .map(food -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", food.id());
                    item.put("name", food.name());
                    item.put("description", food.description());
                    item.put("price", food.price());
                    item.put("imageUrl", food.imageUrl());
                    item.put("available", food.available());
                    item.put("prepTimeMinutes", food.prepTimeMinutes());
                    item.put("categoryId", food.categoryId());
                    item.put("categoryName", food.categoryName());
                    return item;
                })
                .toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("items", items);
        payload.put("limit", limit);

        if (itemId != null) {
            payload.put("item_id", itemId);
        }
        if (preference != null && !preference.isBlank()) {
            payload.put("preference", preference);
        }

        Map<?, ?> response = restClient.post()
                .uri("/recommend-menu")
                .body(payload)
                .retrieve()
                .body(Map.class);

        if (response == null ||
                !(response.get("recommendations") instanceof List<?>)) {
            return List.of();
        }

        return ((List<?>) response.get("recommendations"))
                .stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }
}

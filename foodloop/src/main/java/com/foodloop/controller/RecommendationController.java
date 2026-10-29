
package com.foodloop.controller;

import com.foodloop.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(
            RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public List<Map<String, Object>> recommend(
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) String preference,
            @RequestParam(defaultValue = "5") int limit) {

        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and 20");
        }

        if (itemId == null &&
                (preference == null || preference.isBlank())) {
            preference = "popular tasty food";
        }

        return recommendationService.recommend(
                itemId, preference, limit);
    }
}

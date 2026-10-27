package com.foodloop.service;

import com.foodloop.dto.CatalogDtos.FoodRequest;
import com.foodloop.dto.CatalogDtos.FoodResponse;
import com.foodloop.entity.Category;
import com.foodloop.entity.FoodItem;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.*;
import com.foodloop.util.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FoodService {

    private final FoodItemRepository foodItemRepository;
    private final CategoryRepository categoryRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final SurplusDealRepository dealRepository;

    public FoodService(FoodItemRepository foodItemRepository, CategoryRepository categoryRepository,
                       OrderItemRepository orderItemRepository, CartItemRepository cartItemRepository,
                       SurplusDealRepository dealRepository) {
        this.foodItemRepository = foodItemRepository;
        this.categoryRepository = categoryRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.dealRepository = dealRepository;
    }

    /** Customers only ever see available items; admins may filter by availability or see everything. */
    @Transactional(readOnly = true)
    public List<FoodResponse> search(String name, Long categoryId, Boolean available, boolean isAdmin) {
        Boolean availabilityFilter = isAdmin ? available : Boolean.TRUE;
        String term = name == null ? "" : name.trim();
        return foodItemRepository.search(term, categoryId, availabilityFilter).stream().map(Mapper::toFood).toList();
    }

    @Transactional(readOnly = true)
    public FoodResponse get(Long id, boolean isAdmin) {
        FoodItem f = findOrThrow(id);
        if (!f.isAvailable() && !isAdmin) {
            throw new ResourceNotFoundException("Food item not found with id " + id);
        }
        return Mapper.toFood(f);
    }

    @Transactional
    public FoodResponse create(FoodRequest request) {
        FoodItem f = new FoodItem();
        apply(f, request);
        return Mapper.toFood(foodItemRepository.save(f));
    }

    @Transactional
    public FoodResponse update(Long id, FoodRequest request) {
        FoodItem f = findOrThrow(id);
        apply(f, request);
        return Mapper.toFood(foodItemRepository.save(f));
    }

    @Transactional
    public FoodResponse setAvailability(Long id, boolean available) {
        FoodItem f = findOrThrow(id);
        f.setAvailable(available);
        return Mapper.toFood(foodItemRepository.save(f));
    }

    /** Food that appears in any past order cannot be deleted (history must stay intact) - mark it unavailable instead. */
    @Transactional
    public void delete(Long id) {
        FoodItem f = findOrThrow(id);
        if (orderItemRepository.existsByFoodItemId(id)) {
            throw new ConflictException("This item appears in past orders and cannot be deleted. Mark it unavailable instead.");
        }
        cartItemRepository.deleteByFoodItemId(id);
        dealRepository.deleteByFoodItemId(id);
        foodItemRepository.delete(f);
    }

    private void apply(FoodItem f, FoodRequest r) {
        Category category = categoryRepository.findById(r.categoryId())
                .orElseThrow(() -> new BadRequestException("Category not found with id " + r.categoryId()));
        if (!category.isActive()) {
            throw new BadRequestException("Cannot add food to an inactive category");
        }
        f.setName(r.name().trim());
        f.setDescription(AuthService.blankToNull(r.description()));
        f.setPrice(r.price());
        f.setImageUrl(AuthService.blankToNull(r.imageUrl()));
        f.setAvailable(r.available() == null || r.available());
        f.setPrepTimeMinutes(r.prepTimeMinutes());
        f.setCategory(category);
    }

    private FoodItem findOrThrow(Long id) {
        return foodItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Food item not found with id " + id));
    }
}

package com.foodloop.service;

import com.foodloop.dto.CatalogDtos.CategoryRequest;
import com.foodloop.dto.CatalogDtos.CategoryResponse;
import com.foodloop.entity.Category;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.CategoryRepository;
import com.foodloop.repository.FoodItemRepository;
import com.foodloop.util.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final FoodItemRepository foodItemRepository;

    public CategoryService(CategoryRepository categoryRepository, FoodItemRepository foodItemRepository) {
        this.categoryRepository = categoryRepository;
        this.foodItemRepository = foodItemRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean isAdmin) {
        List<Category> categories = isAdmin ? categoryRepository.findAllByOrderByNameAsc()
                                            : categoryRepository.findByActiveTrueOrderByNameAsc();
        return categories.stream().map(Mapper::toCategory).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id, boolean isAdmin) {
        Category c = findOrThrow(id);
        if (!c.isActive() && !isAdmin) {
            throw new ResourceNotFoundException("Category not found with id " + id);
        }
        return Mapper.toCategory(c);
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        categoryRepository.findByNameIgnoreCase(name).ifPresent(c -> {
            throw new ConflictException("A category named '" + name + "' already exists");
        });
        Category c = new Category();
        apply(c, request, name);
        return Mapper.toCategory(categoryRepository.save(c));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category c = findOrThrow(id);
        String name = request.name().trim();
        categoryRepository.findByNameIgnoreCase(name).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new ConflictException("A category named '" + name + "' already exists");
            }
        });
        apply(c, request, name);
        return Mapper.toCategory(categoryRepository.save(c));
    }

    /** A category that still contains food items cannot be deleted (deactivate it instead). */
    @Transactional
    public void delete(Long id) {
        Category c = findOrThrow(id);
        if (foodItemRepository.existsByCategoryId(id)) {
            throw new ConflictException("Category still has food items. Move/delete them first or deactivate the category.");
        }
        categoryRepository.delete(c);
    }

    private void apply(Category c, CategoryRequest r, String name) {
        c.setName(name);
        c.setDescription(AuthService.blankToNull(r.description()));
        c.setActive(r.active() == null || r.active());
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id " + id));
    }
}

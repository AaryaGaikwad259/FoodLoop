package com.foodloop.controller;

import com.foodloop.dto.CatalogDtos.CategoryRequest;
import com.foodloop.dto.CatalogDtos.CategoryResponse;
import com.foodloop.service.CategoryService;
import com.foodloop.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list(Authentication auth) {
        return categoryService.list(SecurityUtil.isAdmin(auth));
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable Long id, Authentication auth) {
        return categoryService.get(id, SecurityUtil.isAdmin(auth));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

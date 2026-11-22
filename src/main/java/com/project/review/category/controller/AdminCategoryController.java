package com.project.review.category.controller;

import com.project.review.category.dtos.CategoryOrderRequest;
import com.project.review.category.dtos.CategoryRequest;
import com.project.review.category.dtos.CategoryResponse;
import com.project.review.category.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAll() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(categoryService.findAll());
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable("id") Long id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable("id") Long id) {
        categoryService.remove(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    public ResponseEntity<List<CategoryResponse>> reorder(@Valid @RequestBody CategoryOrderRequest request) {
        return ResponseEntity.ok(categoryService.reorder(request.categoryIds()));
    }
}

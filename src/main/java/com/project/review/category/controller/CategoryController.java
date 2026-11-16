package com.project.review.category.controller;

import com.project.review.category.dtos.CategoryRequest;
import com.project.review.category.dtos.CategoryResponse;
import com.project.review.category.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

//    전자기기/IT: 스마트폰, 노트북, 가전, 주변기기
//    뷰티/패션: 화장품, 의류, 신발, 액세서리
//    음식/맛집: 식당, 카페, 배달, 식품
//    여행/숙박: 호텔, 펜션, 여행지, 액티비티
//    문화/콘텐츠: 영화, 드라마, 도서, 게임, 공연
//    생활/리빙: 가구, 인테리어, 생활용품, 육아
//    서비스/기타: 앱, 구독 서비스, 교육, 병원 등

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAll() {
        List<CategoryResponse> categories = categoryService.findAll();
        return ResponseEntity.ok(categories);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryRequest categoryRequest
    ) {
        CategoryResponse categoryResponse = categoryService.create(categoryRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryResponse);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest categoryRequest
    ) {
        CategoryResponse categoryResponse = categoryService.update(id, categoryRequest);
        return ResponseEntity.ok(categoryResponse);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> remove(
            @PathVariable Long id
    ) {
        categoryService.remove(id);
        return ResponseEntity.noContent().build();
    }
}

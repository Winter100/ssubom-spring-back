package com.project.review.review.controller;

import com.project.review.review.dtos.AdminReviewReportTypeResponse;
import com.project.review.review.dtos.ReviewReportTypeCreateRequest;
import com.project.review.review.dtos.ReviewReportTypeOrderRequest;
import com.project.review.review.dtos.ReviewReportTypeUpdateRequest;
import com.project.review.review.service.ReviewReportTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/review-report-types")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReviewReportTypeController {
    private final ReviewReportTypeService types;

    @GetMapping
    public ResponseEntity<List<AdminReviewReportTypeResponse>> findAll() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(types.findAll());
    }

    @PostMapping
    public ResponseEntity<AdminReviewReportTypeResponse> create(
            @Valid @RequestBody ReviewReportTypeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(types.create(request));
    }

    @PutMapping("/order")
    public ResponseEntity<List<AdminReviewReportTypeResponse>> reorder(@Valid @RequestBody ReviewReportTypeOrderRequest request) {
        return ResponseEntity.ok(types.reorder(request.reportTypeIds()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminReviewReportTypeResponse> update(
            @PathVariable("id") Long id, @Valid @RequestBody ReviewReportTypeUpdateRequest request) {
        return ResponseEntity.ok(types.update(id, request));
    }
}

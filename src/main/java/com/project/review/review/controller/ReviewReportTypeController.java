package com.project.review.review.controller;

import com.project.review.review.dtos.ReviewReportTypeResponse;
import com.project.review.review.service.ReviewReportTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/review-report-types")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ReviewReportTypeController {
    private final ReviewReportTypeService types;

    @GetMapping
    public ResponseEntity<List<ReviewReportTypeResponse>> findActive() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(types.findActive());
    }
}

package com.project.review.review.controller;

import com.project.review.auth.service.TokenService;
import com.project.review.review.dtos.ReviewReportRequest;
import com.project.review.review.service.ReviewReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Review Report", description = "로그인 회원의 리뷰 신고 API")
@RestController
@RequestMapping("/api/v1/reviews/{reviewId}/reports")
@RequiredArgsConstructor
public class ReviewReportController {
    private final ReviewReportService reports;
    private final TokenService tokens;

    @PostMapping
    public ResponseEntity<Void> create(@PathVariable("reviewId") Long reviewId,
                                      @Valid @RequestBody ReviewReportRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        reports.create(reviewId, request, tokens.extractMemberId(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}

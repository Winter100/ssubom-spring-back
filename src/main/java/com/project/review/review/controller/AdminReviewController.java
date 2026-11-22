package com.project.review.review.controller;

import com.project.review.auth.service.TokenService;
import com.project.review.review.dtos.ReviewPageResponse;
import com.project.review.review.dtos.ReviewReportPageResponse;
import com.project.review.review.service.ReviewReportService;
import com.project.review.review.service.ReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@Tag(name = "Admin Review", description = "관리자 전용 리뷰 조회 및 삭제 API")
@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;
    private final TokenService tokenService;
    private final ReviewReportService reviewReportService;

    @GetMapping("/reports")
    public ResponseEntity<ReviewReportPageResponse> findReports(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "reviewId", required = false) Long reviewId,
            @RequestParam(name = "confirmed", defaultValue = "false") boolean confirmed
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewReportService.findAll(page, size, reviewId, confirmed));
    }

    @PatchMapping("/reports/{reportId}")
    public ResponseEntity<Void> updateReport(
            @PathVariable("reportId") Long reportId, @RequestBody JsonNode request
    ) {
        JsonNode confirmed = request.path("confirmed");
        if (!confirmed.isBoolean()) {
            throw new IllegalArgumentException("confirmed는 true 또는 false여야 합니다.");
        }
        reviewReportService.updateConfirmed(reportId, confirmed.asBoolean());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<ReviewPageResponse> findAll(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "searchType", defaultValue = "title") String searchType,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "reviewId", required = false) Long reviewId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = tokenService.extractMemberId(jwt);
        return ResponseEntity.ok(reviewService.findAll(page, size, searchType, categoryId, query, memberId, reviewId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable("id") Long id, @AuthenticationPrincipal Jwt jwt) {
        reviewService.removeByAdmin(id, tokenService.extractMemberId(jwt));
        return ResponseEntity.noContent().build();
    }
}

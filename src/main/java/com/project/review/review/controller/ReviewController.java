package com.project.review.review.controller;

import com.project.review.auth.service.TokenService;
import com.project.review.review.dtos.*;
import com.project.review.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Review", description = "리뷰 조회, 생성 및 삭제 API")
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final TokenService tokenService;

    //Todo 스웨거 문서 작성하기

    @GetMapping
    public ResponseEntity<ReviewPageResponse> findAll(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "searchType", defaultValue = "title") String searchType,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "query", required = false) String query,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = jwt != null ? tokenService.extractMemberId(jwt) : null;
        return ResponseEntity.ok(reviewService.findAll(page, size, searchType, categoryId, query, memberId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReviewResponse> findById(
            @Parameter(description = "리뷰 ID", example = "1") @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = jwt != null ? tokenService.extractMemberId(jwt) : null;
        return ResponseEntity.ok().body(reviewService.findByIdAndCountView(id, memberId));
    }

    @GetMapping("/{id}/edit")
    public ResponseEntity<ReviewEditResponse> findByIdEditView(
            @Parameter(description = "리뷰 ID", example = "1") @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = tokenService.extractMemberId(jwt);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewService.findByIdEditView(id, memberId));
    }

    @PostMapping
    public ResponseEntity<Void> create(
            @Valid @RequestBody ReviewRequest reviewRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = tokenService.extractMemberId(jwt);
        reviewService.create(reviewRequest, memberId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody ReviewUpdateRequest reviewUpdateRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = tokenService.extractMemberId(jwt);
        reviewService.update(id, reviewUpdateRequest, memberId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {

        Long memberId = tokenService.extractMemberId(jwt);
        reviewService.remove(id, memberId);

        return ResponseEntity.noContent().build();
    }

    // 리뷰 도움됐어요 등록
    @PutMapping("/{id}/like")
    public ResponseEntity<Void> like(
            @PathVariable("id") Long reviewId,
            @AuthenticationPrincipal Jwt jwt
    ){
        Long memberId = tokenService.extractMemberId(jwt);
        reviewService.insertLike(reviewId, memberId);

        return ResponseEntity.noContent().build();
    }

    // 리뷰 도움됐어요 제거
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> deleteLike(
            @PathVariable("id") Long reviewId,
            @AuthenticationPrincipal Jwt jwt
    ){
        Long memberId = tokenService.extractMemberId(jwt);
        reviewService.deleteLike(reviewId, memberId);

        return ResponseEntity.noContent().build();
    }
}

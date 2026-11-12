package com.project.review.comment.controller;

import com.project.review.auth.service.TokenService;
import com.project.review.comment.dtos.CommentCreateRequest;
import com.project.review.comment.dtos.CommentUpdateRequest;
import com.project.review.comment.dtos.CommentPageResponse;
import com.project.review.comment.dtos.CommentResponse;
import com.project.review.comment.service.CommentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Comment", description = "리뷰 댓글과 대댓글 API")
@RestController
@RequestMapping("/api/v1/reviews/{reviewId}/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService comments;
    private final TokenService tokenService;

    @GetMapping
    public CommentPageResponse findAll(@PathVariable Long reviewId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return comments.findAll(reviewId, page, size);
    }

    @PostMapping
    public ResponseEntity<CommentResponse> create(@PathVariable Long reviewId,
            @Valid @RequestBody CommentCreateRequest request, @AuthenticationPrincipal Jwt jwt) {
        Long memberId = tokenService.extractMemberId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(comments.create(reviewId, request, memberId));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<Void> update(@PathVariable Long reviewId, @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request, @AuthenticationPrincipal Jwt jwt) {
        Long memberId = tokenService.extractMemberId(jwt);
        comments.update(reviewId, commentId, request, memberId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> remove(@PathVariable Long reviewId, @PathVariable Long commentId,
            @AuthenticationPrincipal Jwt jwt) {
        Long memberId = tokenService.extractMemberId(jwt);
        comments.remove(reviewId, commentId, memberId);
        return ResponseEntity.noContent().build();
    }
}

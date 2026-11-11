package com.project.review.member.controller;

import com.project.review.auth.service.TokenService;
import com.project.review.member.dtos.MemberResponse;
import com.project.review.member.dtos.MemberUpdateRequest;
import com.project.review.member.service.MemberService;
import com.project.review.review.dtos.ReviewCategoryCountResponse;
import com.project.review.review.dtos.ReviewPageResponse;
import com.project.review.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final ReviewService reviewService;
    private final TokenService tokenService;

    @GetMapping
    public ResponseEntity<MemberResponse> me(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = tokenService.extractMemberId(jwt);

        return ResponseEntity.ok().body(MemberResponse.from(memberService.findById(memberId)));
    }

    @GetMapping("count")
    public ResponseEntity<List<ReviewCategoryCountResponse>> myReviewCount(
        @AuthenticationPrincipal Jwt jwt
    ){
        Long memberId = tokenService.extractMemberId(jwt);
        return ResponseEntity.ok().body(reviewService.findByMyReviewCount(memberId));
    }

    // Todo 내 정보 업데이트
    @PostMapping
    public void updateMe(
            @Valid @RequestBody MemberUpdateRequest memberUpdateRequest,
            @AuthenticationPrincipal Jwt jwt
    ){
        Long memberId = tokenService.extractMemberId(jwt);

        memberService.updateMe(memberId, memberUpdateRequest);
    }

    @GetMapping("reviews")
    public ResponseEntity<ReviewPageResponse> myReviews(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", defaultValue = "1") String page,
            @RequestParam(name = "size", defaultValue = "50") String size,
            @RequestParam(name = "categoryId", required = false) String categoryId
    ) {
        Long memberId = tokenService.extractMemberId(jwt);

        return ResponseEntity.ok(reviewService.findReviewByMemberId(memberId, page, size, categoryId));
    }

}

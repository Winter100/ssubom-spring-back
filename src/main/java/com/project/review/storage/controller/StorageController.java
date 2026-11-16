package com.project.review.storage.controller;

import com.project.review.auth.service.TokenService;
import com.project.review.member.entities.Member;
import com.project.review.member.service.MemberService;
import com.project.review.storage.dtos.ImageUploadBatchRequest;
import com.project.review.storage.dtos.ImageUploadResponse;
import com.project.review.storage.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final TokenService tokenService;
    private final MemberService memberService;

    /**
     * presigner-url 발급 요청
     */
    @PostMapping("/presigned-url")
    public ResponseEntity<List<ImageUploadResponse>> uploadUrl(
            @Valid @RequestBody ImageUploadBatchRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = tokenService.extractMemberId(jwt);
        Member member = memberService.findById(memberId);
        return ResponseEntity.ok(
                storageService.uploadUrls(request, member)
        );
    }
}
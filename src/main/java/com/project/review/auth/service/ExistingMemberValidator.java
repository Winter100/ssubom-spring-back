package com.project.review.auth.service;

import com.project.review.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** 서명이 유효해도 회원이 완전히 삭제된 토큰으로는 인증할 수 없습니다. */
@RequiredArgsConstructor
public class ExistingMemberValidator implements OAuth2TokenValidator<Jwt> {
    private final MemberRepository members;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            long memberId = Long.parseLong(token.getSubject());
            if (memberId > 0 && members.existsById(memberId)) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (NumberFormatException exception) {
            // sub가 없거나 숫자가 아니어도 인증 실패로 처리합니다.
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "유효하지 않은 회원 인증 정보입니다.", null));
    }
}

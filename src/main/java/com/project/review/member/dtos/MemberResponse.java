package com.project.review.member.dtos;

import com.project.review.member.entities.Member;

import java.time.Instant;

public record MemberResponse (
        Long id,
        String email,
        String nickname,
        Instant createdAt,
        String profileImage,
        Instant nicknameChangedAt
) {

    public static MemberResponse from(Member member){
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getCreatedAt(),
                member.getProfileImage(),
                member.getNicknameChangedAt()
        );
    }
}

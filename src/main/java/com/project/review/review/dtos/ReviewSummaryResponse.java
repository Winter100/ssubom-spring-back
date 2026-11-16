package com.project.review.review.dtos;

import com.project.review.category.dtos.CategoryResponse;
import com.project.review.member.dtos.MemberSummaryResponse;

import java.math.BigDecimal;
import java.time.Instant;

public record ReviewSummaryResponse(
        Long id,
        String title,
        Instant createdAt,
        BigDecimal rating,
        MemberSummaryResponse author,
        CategoryResponse category,
        String mainImageKey,
        long viewCount,
        boolean isLike,
        long likeCount
) {

    // JPQL에서 선택한 필드만 받아 기존 작성자·카테고리 응답 구조를 유지합니다.
    public ReviewSummaryResponse(
            Long id,
            String title,
            Instant createdAt,
            BigDecimal rating,
            Long authorId,
            String nickname,
            String profileImage,
            Long categoryId,
            String categoryName,
            String mainImageKey,
            boolean isLike
    ) {
        this(
                id,
                title,
                createdAt,
                rating,
                new MemberSummaryResponse(authorId, nickname, profileImage),
                new CategoryResponse(categoryId, categoryName),
                mainImageKey,
                0L,
                isLike,
                0L
        );
    }

    public ReviewSummaryResponse withStats(ReviewStats stats) {
        return new ReviewSummaryResponse(
                id, title, createdAt, rating,
                author, category, mainImageKey,
                stats.viewCount(), stats.isLike(), stats.countLike()
        );
    }
}

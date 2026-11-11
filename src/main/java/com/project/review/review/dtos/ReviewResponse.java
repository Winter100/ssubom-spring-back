package com.project.review.review.dtos;

import com.project.review.category.dtos.CategoryResponse;
import com.project.review.member.dtos.MemberSummaryResponse;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewImage;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "리뷰 정보와 공개 이미지 URL")
public record ReviewResponse(
        Long id,
        String title,
        CategoryResponse category,
        String product,
        JsonNode content,
        BigDecimal rating,
        Instant createdAt,
        String duration,
        List<String> pros,
        List<String> cons,
        String verdict,
        String mainImageKey,
        List<String> subImageKeys,
        MemberSummaryResponse author,
        long viewCount,
        boolean isLike,
        Long likeCount
) {

    public static ReviewResponse from(Review review, ReviewStats stats) {
        String mainImageKey = review.getImages().stream()
                .filter(image -> image.getDisplayOrder() == 0)
                .findFirst()
                .map(ReviewImage::getImageKey)
                .orElse(null);

        return new ReviewResponse(
                review.getId(),
                review.getTitle(),
                CategoryResponse.from(review.getCategory()),
                review.getProduct(),
                review.getContent(),
                review.getRating(),
                review.getCreatedAt(),
                review.getDuration(),
                review.getPros(),
                review.getCons(),
                review.getVerdict(),
                mainImageKey,
                review.getImages().stream().filter(image -> image.getDisplayOrder() > 0)
                        .map(ReviewImage::getImageKey).toList(),
                MemberSummaryResponse.from(review.getAuthor()),
                stats.viewCount(),
                stats.isLike(),
                stats.countLike()
        );
    }
}

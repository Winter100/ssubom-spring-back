package com.project.review.review.dtos;

import com.project.review.category.dtos.CategoryResponse;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewImage;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

public record ReviewEditResponse(
        Long id,
        String title,
        CategoryResponse category,
        String product,
        JsonNode content,
        BigDecimal rating,
        String duration,
        List<String> pros,
        List<String> cons,
        String verdict,
        String mainImageKey
) {
    public static ReviewEditResponse from(Review review) {
        String mainImageKey = review.getImages().stream()
                .filter(image -> image.getDisplayOrder() == 0)
                .findFirst()
                .map(ReviewImage::getImageKey)
                .orElse(null);

        return new ReviewEditResponse(
                review.getId(), review.getTitle(), CategoryResponse.from(review.getCategory()),
                review.getProduct(), review.getContent(), review.getRating(), review.getDuration(),
                review.getPros(), review.getCons(), review.getVerdict(), mainImageKey
        );
    }
}

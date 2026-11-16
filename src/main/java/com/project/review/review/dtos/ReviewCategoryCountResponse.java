package com.project.review.review.dtos;

public record ReviewCategoryCountResponse (
        Long categoryId,
        String category,
        Long count
) {
}

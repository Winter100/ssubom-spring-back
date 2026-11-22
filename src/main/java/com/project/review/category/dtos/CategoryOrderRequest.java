package com.project.review.category.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CategoryOrderRequest(
        @NotNull(message = "카테고리 ID 목록은 필수입니다.")
        List<@NotNull @Positive Long> categoryIds
) {
}

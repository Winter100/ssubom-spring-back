package com.project.review.storage.dtos;

import jakarta.validation.constraints.*;

public record ImageUploadRequest(
        @NotBlank(message = "name은 필수입니다.")
        String name,

        @NotBlank(message = "contentType은 필수입니다.")
        String contentType, // image/jpeg, image/png, image/webp 등

        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        @Max(value = 5 * 1024 * 1024, message = "이미지 크기는 최대 5MB입니다.")
        Long fileSize, // 바이트 단위

        @NotBlank
        String clientId
) {}

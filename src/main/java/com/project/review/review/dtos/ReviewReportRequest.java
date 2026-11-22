package com.project.review.review.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReviewReportRequest(
        @NotNull @Positive Long reportTypeId,
        @NotBlank @Size(max = 1000) String reason
) {
    public ReviewReportRequest {
        reason = reason == null ? null : reason.strip();
    }
}

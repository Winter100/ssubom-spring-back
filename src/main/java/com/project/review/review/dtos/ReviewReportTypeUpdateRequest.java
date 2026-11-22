package com.project.review.review.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewReportTypeUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(0) Integer displayOrder,
        @NotNull Boolean active
) {
    public ReviewReportTypeUpdateRequest {
        name = name == null ? null : name.strip();
    }
}

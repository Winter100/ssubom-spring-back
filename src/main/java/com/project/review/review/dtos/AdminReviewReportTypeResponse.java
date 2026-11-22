package com.project.review.review.dtos;

import com.project.review.review.entities.ReviewReportType;

public record AdminReviewReportTypeResponse(Long id, String name, int displayOrder, boolean active) {
    public static AdminReviewReportTypeResponse from(ReviewReportType type) {
        return new AdminReviewReportTypeResponse(type.getId(), type.getName(), type.getDisplayOrder(), type.isActive());
    }
}

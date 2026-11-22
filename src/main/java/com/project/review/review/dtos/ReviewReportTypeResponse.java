package com.project.review.review.dtos;

import com.project.review.review.entities.ReviewReportType;

public record ReviewReportTypeResponse(Long id, String name, int displayOrder) {
    public static ReviewReportTypeResponse from(ReviewReportType type) {
        return new ReviewReportTypeResponse(type.getId(), type.getName(), type.getDisplayOrder());
    }
}

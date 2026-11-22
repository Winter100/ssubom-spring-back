package com.project.review.review.dtos;

import org.springframework.data.domain.Page;

import java.util.List;

public record ReviewReportPageResponse(
        List<ReviewReportResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static ReviewReportPageResponse from(Page<ReviewReportResponse> reports) {
        return new ReviewReportPageResponse(reports.getContent(), reports.getNumber() + 1,
                reports.getSize(), reports.getTotalElements(), reports.getTotalPages());
    }
}

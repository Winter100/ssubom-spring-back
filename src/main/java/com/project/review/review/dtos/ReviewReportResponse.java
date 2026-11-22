package com.project.review.review.dtos;

import com.project.review.member.dtos.MemberSummaryResponse;

import java.time.Instant;

public record ReviewReportResponse(
        Long id,
        Long reviewId,
        String reviewTitle,
        MemberSummaryResponse author,
        MemberSummaryResponse reporter,
        ReportType reportType,
        String reason,
        Instant createdAt,
        boolean confirmed
) {
    public ReviewReportResponse(Long id, Long reviewId, String reviewTitle,
                               Long authorId, String authorNickname, String authorImage,
                               Long reporterId, String reporterNickname, String reporterImage,
                               Long reportTypeId, String reportTypeName,
                               String reason, Instant createdAt, boolean confirmed) {
        this(id, reviewId, reviewTitle,
                new MemberSummaryResponse(authorId, authorNickname, authorImage),
                reporterId == null ? null : new MemberSummaryResponse(reporterId, reporterNickname, reporterImage),
                new ReportType(reportTypeId, reportTypeName),
                reason, createdAt, confirmed);
    }

    public record ReportType(Long id, String name) {}
}

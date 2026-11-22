package com.project.review.review.service;

import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.member.service.MemberService;
import com.project.review.review.dtos.ReviewReportPageResponse;
import com.project.review.review.dtos.ReviewReportRequest;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewReport;
import com.project.review.review.entities.ReviewReportType;
import com.project.review.review.repository.ReviewReportRepository;
import com.project.review.review.repository.ReviewReportTypeRepository;
import com.project.review.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ReviewReportService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final ReviewReportRepository reports;
    private final ReviewReportTypeRepository types;
    private final ReviewRepository reviews;
    private final MemberService members;
    private final Clock clock;

    @Transactional
    public void create(Long reviewId, ReviewReportRequest request, Long memberId) {
        Member reporter = members.findByIdForContentWrite(memberId);
        Review review = lockReviewForReport(reviewId, memberId);
        ReviewReportType reportType = lockActiveReportType(request.reportTypeId());

        Instant reportedAt = clock.instant();
        LocalDate reportDate = reportedAt.atZone(SEOUL).toLocalDate();
        if (reports.existsByReviewIdAndReporterIdAndReportDate(reviewId, memberId, reportDate)) {
            throw new BusinessException(ErrorCode.REVIEW_REPORT_ALREADY_EXISTS);
        }

        reports.saveAndFlush(new ReviewReport(review, reporter, reportType, request.reason(), reportDate, reportedAt));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ReviewReportPageResponse findAll(int page, int size, Long reviewId, boolean confirmed) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("페이지는 1 이상, 크기는 1~100이어야 합니다.");
        }
        PageRequest pageable = PageRequest.of(page - 1, size, NEWEST_FIRST);
        return ReviewReportPageResponse.from(reports.findSummaries(reviewId, confirmed, pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void updateConfirmed(Long reportId, boolean confirmed) {
        if (reports.updateConfirmed(reportId, confirmed) == 0) {
            throw new BusinessException(ErrorCode.REVIEW_REPORT_NOT_FOUND);
        }
    }

    private Review lockReviewForReport(Long reviewId, Long reporterId) {
        Review review = reviews.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (review.getAuthor().getId().equals(reporterId)) {
            throw new BusinessException(ErrorCode.REVIEW_REPORT_SELF_NOT_ALLOWED);
        }
        return review;
    }

    private ReviewReportType lockActiveReportType(Long reportTypeId) {
        // 신고 트랜잭션이 끝날 때까지 읽기 잠금을 유지해 관리자의 비활성화와 순서를 보장합니다.
        ReviewReportType reportType = types.findByIdForReport(reportTypeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_REPORT_TYPE_NOT_FOUND));
        if (!reportType.isActive()) {
            throw new BusinessException(ErrorCode.REVIEW_REPORT_TYPE_INACTIVE);
        }
        return reportType;
    }
}

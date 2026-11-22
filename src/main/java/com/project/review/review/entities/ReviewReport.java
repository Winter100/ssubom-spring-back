package com.project.review.review.entities;

import com.project.review.member.entities.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "review_reports", uniqueConstraints = @UniqueConstraint(
        name = "uk_review_reports_daily", columnNames = {"review_id", "reporter_id", "report_date"}
), indexes = {
        @Index(name = "idx_review_reports_created", columnList = "created_at DESC, id DESC"),
        @Index(name = "idx_review_reports_review_created", columnList = "review_id, created_at DESC, id DESC"),
        @Index(name = "idx_review_reports_reporter", columnList = "reporter_id"),
        @Index(name = "idx_review_reports_type", columnList = "report_type_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private Member reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_type_id", nullable = false)
    private ReviewReportType reportType;

    @NotBlank
    @Size(max = 1000)
    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "report_date", nullable = false, updatable = false)
    private LocalDate reportDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean confirmed;

    public ReviewReport(Review review, Member reporter, ReviewReportType reportType,
                        String reason, LocalDate reportDate, Instant createdAt) {
        this.review = review;
        this.reporter = reporter;
        this.reportType = reportType;
        this.reason = reason;
        this.reportDate = reportDate;
        this.createdAt = createdAt;
    }
}

package com.project.review.review.repository;

import com.project.review.review.dtos.ReviewReportResponse;
import com.project.review.review.entities.ReviewReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {
    boolean existsByReviewIdAndReporterId(Long reviewId, Long reporterId);

    boolean existsByReviewIdAndReporterIdAndReportDate(Long reviewId, Long reporterId, LocalDate reportDate);

    @Query(value = """
            select new com.project.review.review.dtos.ReviewReportResponse(
                p.id, r.id, r.title, a.id, a.nickname, a.profileImage,
                m.id, m.nickname, m.profileImage, t.id, t.name, p.reason, p.createdAt, p.confirmed
            )
            from ReviewReport p
            join p.review r
            join r.author a
            left join p.reporter m
            join p.reportType t
            where p.confirmed = :confirmed and (:reviewId is null or r.id = :reviewId)
            """, countQuery = """
            select count(p) from ReviewReport p where p.confirmed = :confirmed and (:reviewId is null or p.review.id = :reviewId)
            """)
    Page<ReviewReportResponse> findSummaries(
            @Param("reviewId") Long reviewId, @Param("confirmed") boolean confirmed, Pageable pageable);

    @Modifying
    @Query("update ReviewReport p set p.confirmed = :confirmed where p.id = :id")
    int updateConfirmed(@Param("id") Long id, @Param("confirmed") boolean confirmed);

    @Modifying
    @Query("delete from ReviewReport p where p.review.id = :reviewId")
    void deleteAllByReviewId(@Param("reviewId") Long reviewId);

    @Modifying
    @Query("update ReviewReport p set p.reporter = null where p.reporter.id = :memberId")
    void anonymizeByReporterId(@Param("memberId") Long memberId);
}

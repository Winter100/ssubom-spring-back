package com.project.review.review.repository;

import com.project.review.review.entities.ReviewReportType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewReportTypeRepository extends JpaRepository<ReviewReportType, Long> {
    List<ReviewReportType> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    List<ReviewReportType> findAllByOrderByDisplayOrderAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ReviewReportType t order by t.id")
    List<ReviewReportType> findAllForUpdate();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from ReviewReportType t where t.id = :id")
    Optional<ReviewReportType> findByIdForReport(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ReviewReportType t where t.id = :id")
    Optional<ReviewReportType> findByIdForUpdate(@Param("id") Long id);
}

package com.project.review.comment.repository;

import com.project.review.comment.entities.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = "author")
    Page<Comment> findByReviewIdAndParentIsNull(Long reviewId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    List<Comment> findByParentIdInOrderByCreatedAtAscIdAsc(List<Long> parentIds);

    Optional<Comment> findByIdAndReviewId(Long id, Long reviewId);

    @Modifying
    @Query("delete from Comment c where c.review.id = :reviewId and c.parent is not null")
    void deleteRepliesByReviewId(@Param("reviewId") Long reviewId);

    @Modifying
    @Query("delete from Comment c where c.review.id = :reviewId")
    void deleteAllByReviewId(@Param("reviewId") Long reviewId);
}

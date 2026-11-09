package com.project.review.review.repository;

import com.project.review.review.entities.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {
    boolean existsByReviewIdAndMemberId(Long reviewId, Long memberId);

    @Query("""
                select rl.review.id
                from ReviewLike rl
                where rl.member.id = :memberId
                and rl.review.id in (:reviewIds)
            """)
    Set<Long> findLikedReviewIds(
            @Param("memberId") Long memberId,
            @Param("reviewIds") List<Long> reviewIds
    );


    @Modifying(clearAutomatically = true)
    @Query(value = """
                insert into reviews_like (member_id, review_id, created_at)
                values (:memberId, :reviewId, CURRENT_TIMESTAMP)
                on conflict (member_id, review_id) do nothing
            """, nativeQuery = true)
    void insertLike(@Param("memberId") Long memberId,
                   @Param("reviewId") Long reviewId);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            delete from ReviewLike rl
            where rl.member.id = :memberId and rl.review.id = :reviewId
            """)
    void deleteLike(@Param("memberId") Long memberId,
                   @Param("reviewId") Long reviewId);
}

package com.project.review.review.repository;

import com.project.review.review.dtos.ReviewCategoryCountResponse;
import com.project.review.review.dtos.ReviewSummaryResponse;
import com.project.review.review.entities.Review;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    Optional<Review> findByIdForUpdate(@Param("id") Long id);

    @Query("select r.id from Review r where r.author.id = :memberId and r.id > :afterId order by r.id")
    List<Long> findIdsByAuthor(@Param("memberId") Long memberId, @Param("afterId") Long afterId, Pageable pageable);

    interface ContentReference {
        Long getId();

        JsonNode getContent();
    }

    @Query("select r.id as id, r.content as content from Review r where r.id > :afterId order by r.id")
    List<ContentReference> findContentReferences(@Param("afterId") Long afterId, Pageable pageable);

    @Query("select distinct i.imageKey from ReviewImage i where i.imageKey in :keys")
    Set<String> findReferencedImageKeys(@Param("keys") Set<String> keys);

    @Query(value = """
            select new com.project.review.review.dtos.ReviewSummaryResponse(
                r.id, r.title, r.createdAt, r.rating,
                a.id, a.nickname, a.profileImage, c.id, c.category, i.imageKey, false
            )
            from Review r
            join r.author a
            join r.category c
            left join r.images i on i.displayOrder = 0
            """, countQuery = "select count(r) from Review r")
    Page<ReviewSummaryResponse> findAllSummaries(Pageable pageable);

    @Query(value = """
            select new com.project.review.review.dtos.ReviewSummaryResponse(
                r.id, r.title, r.createdAt, r.rating,
                a.id, a.nickname, a.profileImage, c.id, c.category, i.imageKey, false
            )
            from Review r
            join r.author a
            join r.category c
            left join r.images i on i.displayOrder = 0
            where (:categoryId is null or c.id = :categoryId)
              and (:titleQuery = '' or locate(:titleQuery, r.title) > 0)
              and (:authorQuery = '' or a.nickname = :authorQuery)
            """, countQuery = """
            select count(r)
            from Review r
            join r.author a
            join r.category c
            where (:categoryId is null or c.id = :categoryId)
              and (:titleQuery = '' or locate(:titleQuery, r.title) > 0)
              and (:authorQuery = '' or a.nickname = :authorQuery)
            """)
    Page<ReviewSummaryResponse> findAllSummaries(
            @Param("categoryId") Long categoryId,
            @Param("titleQuery") String titleQuery,
            @Param("authorQuery") String authorQuery,
            Pageable pageable
    );

    @Override
    @EntityGraph(attributePaths = {"author", "category", "images"})
    List<Review> findAll();

    @Override
    @EntityGraph(attributePaths = {"author", "category", "images"})
    Optional<Review> findById(Long id);

    @Query(value = """
            select new com.project.review.review.dtos.ReviewSummaryResponse(
                r.id, r.title, r.createdAt, r.rating,
                a.id, a.nickname,a.profileImage, c.id, c.category, i.imageKey, false
            )
            from Review r
            join r.author a
            join r.category c
            left join r.images i on i.displayOrder = 0
            where a.id = :memberId
              and (:categoryId is null or c.id = :categoryId)
            """, countQuery = """
            select count(r) from Review r
            where r.author.id = :memberId
              and (:categoryId is null or r.category.id = :categoryId)
            """)
    Page<ReviewSummaryResponse> findAllSummariesByAuthorId(
            @Param("memberId") Long memberId,
            @Param("categoryId") Long categoryId,
            Pageable pageable);

    @Query(value = """
            select c.id, c.category, count(r)
            from Category c
            left join c.reviews r on r.author.id = :memberId
            group by c.id, c.category
            order by c.id
            """)
    List<ReviewCategoryCountResponse> findCategoryCountsByAuthorId(
            @Param("memberId") Long memberId
    );
}

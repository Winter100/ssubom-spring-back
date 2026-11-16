package com.project.review.review.entities;

import com.project.review.category.entities.Category;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.dtos.ReviewUpdateRequest;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private Long version;

    @Column(nullable = false)
    @Size(min = 1, max = 30)
    private String title;

    @Column(nullable = false)
    private String product;

    @Column()
    // 구매 또는 사용 횟수
    private String duration;

    @Column
    // 이런점이 좋았어요
    private List<String> pros;

    @Column
    // 이런점은 아쉬웠어요
    private List<String> cons;

    @Column
    // 이런분께 추천해요
    private String verdict;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode content;

    @DecimalMin("0")
    @DecimalMax("5")
    @Column(nullable = false)
    private BigDecimal rating;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // 리뷰 저장 시 이미지도 함께 저장하며, 리뷰 삭제 시 연결된 이미지 행을 삭제합니다.
    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ReviewImage> images = new ArrayList<>();

    public void addImage(String imageKey, int displayOrder) {
        images.add(new ReviewImage(this, imageKey, displayOrder));
    }

    public void clearImages() {
        images.clear();
    }

    public static Review create(ReviewRequest reviewRequest, Member member, Category category){

        validateRating(reviewRequest.rating());

        return Review.builder()
                .title(reviewRequest.title())
                .content(reviewRequest.content())
                .rating(reviewRequest.rating())
                .cons(reviewRequest.cons())
                .pros(reviewRequest.pros())
                .product(reviewRequest.product())
                .duration(reviewRequest.duration())
                .verdict(reviewRequest.verdict())
                .author(member)
                .category(category)
                .build();
    }

    @Builder(access = AccessLevel.PROTECTED)
    private Review(String title,
                  JsonNode content,
                  BigDecimal rating,
                  Member author,
                  Category category,
                  String duration,
                  List<String> pros,
                  List<String> cons,
                  String verdict,
                  String product
    ) {
        this.title = title;
        this.content = content;
        this.rating = rating;
        this.author = author;
        this.category = category;
        this.duration = duration;
        this.pros = pros;
        this.cons = cons;
        this.verdict = verdict;
        this.product = product;
    }

    public void updateDetails(String title, JsonNode content, BigDecimal rating, String product, Category category,
                              String duration, List<String> pros, List<String> cons, String verdict) {

        validateRating(rating);

        this.title = title;
        this.content = content;
        this.rating = rating;
        this.product = product;
        this.category = category;
        this.duration = duration;
        this.pros = pros;
        this.cons = cons;
        this.verdict = verdict;
    }

    public void validateAuthor(Long memberId) {
        if (!author.getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static void validateRating(BigDecimal rating) {
        if (rating == null
                || rating.compareTo(BigDecimal.ZERO) < 0
                || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new IllegalArgumentException("평점은 0~5 사이여야 합니다.");
        }
    }

}

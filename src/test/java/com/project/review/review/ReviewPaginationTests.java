package com.project.review.review;

import com.project.review.review.dtos.ReviewRequest;

import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewLike;
import com.project.review.review.repository.ReviewLikeRepository;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.repository.ReviewViewRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.project.review.review.ReviewContentFixtures.textDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:pagination-tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true",
        "spring.jpa.open-in-view=false",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.access-token-expires-minutes=15",
        "security.jwt.refresh-token-expires-days=7",
        "storage.endpoint=https://example.com",
        "storage.bucket=test-bucket",
        "storage.access-key=test-access-key",
        "storage.secret-key=test-secret-key",
        "storage.public-base-url=https://images.example.com"
})
@AutoConfigureMockMvc
@Transactional
class ReviewPaginationTests {

    // PostgreSQL 전용 집계 SQL은 ReviewViewPostgresTests에서 검증합니다.
    @MockitoBean
    private ReviewViewRepository reviewViews;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MemberRepository members;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private ReviewRepository reviews;
    @Autowired
    private ReviewLikeRepository likes;
    @Autowired
    private EntityManager entityManager;

    @Test
    void returnsRequestedPagesWithImagesAndOneBasedMetadata() throws Exception {
        Member author = members.save(Member.create("pages@example.com", "page-user", "unused"));
        Category category = categories.save(new Category("페이지 테스트"));
        for (int i = 1; i <= 5; i++) {
            Review review = Review.create(new ReviewRequest("리뷰 " + i, "Product", textDocument("내용"), BigDecimal.ONE,
                null, null, null, null, null, null, null),
                author, category);
            review.addImage("reviews/main-" + i, 0);
            review.addImage("reviews/sub-" + i, 1);
            reviews.save(review);
        }
        reviews.flush();
        // 동일한 작성 시간에서도 ID로 순서가 결정되는지 검증합니다.
        entityManager.createQuery("update Review r set r.createdAt = :time")
                .setParameter("time", Instant.parse("2026-01-01T00:00:00Z"))
                .executeUpdate();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mvc.perform(get("/api/v1/reviews").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("리뷰 5"))
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.content[0].rating").value(1))
                .andExpect(jsonPath("$.content[0].author.nickname").value("page-user"))
                .andExpect(jsonPath("$.content[0].category.category").value("페이지 테스트"))
                .andExpect(jsonPath("$.content[0].mainImageKey").value("reviews/main-5"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist())
                .andExpect(jsonPath("$.content[0].subImageKeys").doesNotExist());
        // 목록·페이지 COUNT·좋아요 집계만 실행하고 엔티티는 로딩하지 않습니다.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(statistics.getEntityLoadCount()).isZero();
        mvc.perform(get("/api/v1/reviews").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.content[0].title").value("리뷰 3"));
        mvc.perform(get("/api/v1/reviews").param("page", "3").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("리뷰 1"));
        mvc.perform(get("/api/v1/reviews").param("page", "4").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(4))
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/reviews", "/api/v1/member/reviews"})
    void returnsLikeCountsIncludingZeroForEachReview(String endpoint) throws Exception {
        Member author = members.save(Member.create("likes-author@example.com", "author", "unused"));
        Member other = members.save(Member.create("likes-other@example.com", "other", "unused"));
        Category category = categories.save(new Category("좋아요 집계"));
        Review liked = reviews.save(Review.create(new ReviewRequest("liked", "Product", textDocument("body"), BigDecimal.ONE,
                null, null, null, null, null, null, null),
                author, category));
        Review unliked = reviews.save(Review.create(new ReviewRequest("unliked", "Product", textDocument("body"), BigDecimal.ONE,
                null, null, null, null, null, null, null),
                author, category));
        liked.addImage("reviews/main", 0);
        liked.addImage("reviews/sub", 1);
        likes.saveAll(List.of(new ReviewLike(liked, author), new ReviewLike(liked, other)));
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get(endpoint).with(jwt().jwt(token -> token.subject(author.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[?(@.id == %s)].likeCount", liked.getId()).value(2))
                .andExpect(jsonPath("$.content[?(@.id == %s)].likeCount", unliked.getId()).value(0));

        mvc.perform(get(endpoint).with(jwt().jwt(token -> token.subject(author.getId().toString())))
                        .param("categoryId", category.getId().toString()).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(unliked.getId()))
                .andExpect(jsonPath("$.content[0].likeCount").value(0));

        if (endpoint.equals("/api/v1/reviews")) {
            mvc.perform(get(endpoint))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.id == %s)].likeCount", liked.getId()).value(2))
                    .andExpect(jsonPath("$.content[?(@.id == %s)].isLike", liked.getId()).value(false));
        }
    }

    @Test
    void retainsReviewWithoutMainImageAndKeepsDetailedResponse() throws Exception {
        Member author = members.save(Member.create("no-image@example.com", "no-image-user", "unused"));
        Category category = categories.save(new Category("이미지 없음"));
        Review review = reviews.saveAndFlush(Review.create(new ReviewRequest("이미지 없는 리뷰", "Product", textDocument("상세 본문"), BigDecimal.ONE,
                null, null, null, null, null, null, null),
                author, category));
        Long id = review.getId();
        entityManager.clear();

        mvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].mainImageKey").isEmpty())
                .andExpect(jsonPath("$.content[0].content").doesNotExist());
        mvc.perform(get("/api/v1/reviews/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.type").value("doc"))
                .andExpect(jsonPath("$.content.content[0].content[0].text").value("상세 본문"))
                .andExpect(jsonPath("$.subImageKeys").doesNotExist());
    }

    @Test
    void returnsOnlyMemberReviewSummariesWithoutLoadingEntities() throws Exception {
        Member author = members.save(Member.create("my-reviews@example.com", "my-review-user", "unused"));
        Member other = members.save(Member.create("other-reviews@example.com", "other-user", "unused"));
        Category category = categories.save(new Category("내 리뷰"));
        Review review = Review.create(new ReviewRequest("대표 이미지 리뷰", "Product", textDocument("본문"), BigDecimal.ONE,
                null, null, null, null, null, null, null),
                author, category);
        review.addImage("reviews/main", 0);
        review.addImage("reviews/sub", 1);
        reviews.save(review);
        Review withoutImage = reviews.save(Review.create(new ReviewRequest("이미지 없는 리뷰", "Product", textDocument("본문"), BigDecimal.ONE,
                null, null, null, null, null, null, null),
                author, category));
        reviews.saveAndFlush(Review.create(new ReviewRequest("다른 회원 리뷰", "Product", textDocument("본문"), BigDecimal.ONE,
                null, null, null, null, null, null, null),
                other, category));
        entityManager.clear();

        when(reviewViews.findCounts(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of(review.getId(), 7L));
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mvc.perform(get("/api/v1/member/reviews").with(jwt()
                        .jwt(token -> token.subject(author.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[?(@.id == %s)].mainImageKey", review.getId()).value("reviews/main"))
                .andExpect(jsonPath("$.content[?(@.id == %s)].title", withoutImage.getId()).value("이미지 없는 리뷰"))
                .andExpect(jsonPath("$.content[0].author.nickname").value("my-review-user"))
                .andExpect(jsonPath("$.content[0].category.category").value("내 리뷰"))
                .andExpect(jsonPath("$.content[?(@.id == %s)].viewCount", review.getId()).value(7))
                .andExpect(jsonPath("$.content[?(@.id == %s)].viewCount", withoutImage.getId()).value(0))
                .andExpect(jsonPath("$.content[0].content").doesNotExist())
                .andExpect(jsonPath("$.content[0].subImageKeys").doesNotExist());

        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        verify(reviewViews).findCounts(org.mockito.ArgumentMatchers.argThat(
                ids -> ids.size() == 2 && ids.containsAll(List.of(review.getId(), withoutImage.getId()))));

        mvc.perform(get("/api/v1/member/reviews").with(jwt()
                        .jwt(token -> token.subject(author.getId().toString())))
                        .param("categoryId", category.getId().toString()).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(withoutImage.getId()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.size").value(1));

        for (String invalidPage : List.of("abc", "0", "2147483648")) {
            mvc.perform(get("/api/v1/member/reviews").with(jwt()
                            .jwt(token -> token.subject(author.getId().toString())))
                            .param("page", invalidPage).param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(50))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(withoutImage.getId()));
        }

        for (String invalidCategory : List.of("abc", "-1", "9223372036854775808")) {
            mvc.perform(get("/api/v1/member/reviews").with(jwt()
                            .jwt(token -> token.subject(author.getId().toString())))
                            .param("categoryId", invalidCategory).param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(50))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        mvc.perform(get("/api/v1/member/reviews").with(jwt()
                        .jwt(token -> token.subject("9223372036854775807"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void defaultsToFirstPageAndFiftyItemsForEmptyList() throws Exception {
        mvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "2147483648"})
    void rejectsInvalidPage(String page) throws Exception {
        mvc.perform(get("/api/v1/reviews").param("page", page))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "abc"})
    void rejectsInvalidSize(String size) throws Exception {
        mvc.perform(get("/api/v1/reviews").param("size", size))
                .andExpect(status().isBadRequest());
    }
}

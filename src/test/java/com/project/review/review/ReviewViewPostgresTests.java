package com.project.review.review;

import com.project.review.review.dtos.ReviewRequest;

import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.repository.ReviewViewRepository;
import com.project.review.review.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.project.review.review.ReviewContentFixtures.textDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 반드시 폐기 가능한 PostgreSQL DB URL을 전달해야 합니다. 이 테스트는 스키마를 생성·삭제합니다. */
@EnabledIfEnvironmentVariable(named = "REVIEW_VIEWS_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=${REVIEW_VIEWS_TEST_URL}",
        "spring.datasource.username=${REVIEW_VIEWS_TEST_USER:review_views_test}",
        "spring.datasource.password=${REVIEW_VIEWS_TEST_PASSWORD:review_views_test}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.access-token-expires-minutes=15", "security.jwt.refresh-token-expires-days=7",
        "storage.endpoint=https://example.com", "storage.bucket=test-bucket",
        "storage.access-key=test", "storage.secret-key=test", "storage.public-base-url=https://images.example.com"
})
@AutoConfigureMockMvc
class ReviewViewPostgresTests {

    @Autowired private MockMvc mvc;
    @Autowired private ReviewService service;
    @Autowired private ReviewRepository reviews;
    @Autowired private MemberRepository members;
    @Autowired private CategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @MockitoSpyBean private ReviewViewRepository views;

    private Long reviewId;
    private Member author;
    private Category category;

    @BeforeEach
    void setUp() {
        reviews.deleteAll();
        members.deleteAll();
        categories.deleteAll();
        author = members.save(Member.create("views@example.com", "viewer", "unused"));
        category = categories.save(new Category("조회수"));
        reviewId = saveReview();
    }

    @Test
    void concurrentRequestsDoNotLoseIncrements() throws Exception {
        runConcurrent(16);
        assertThat(views.findCount(reviewId)).isEqualTo(16);
    }

    @Test
    void rollsBackCounterOnFailure() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("injected failure after increment");
        }).when(views).increment(reviewId);
        assertThatThrownBy(() -> service.findByIdAndCountView(reviewId, null))
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(views.findCount(reviewId)).isZero();
    }

    @Test
    void listingAndReadOnlyLookupDoNotCountAndMissingReviewReturns404() throws Exception {
        assertThat(service.findByIdEditView(reviewId, author.getId()).id()).isEqualTo(reviewId);
        assertThat(views.findCount(reviewId)).isZero();
        mvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].viewCount").value(0));
        mvc.perform(get("/api/v1/reviews/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        service.findByIdAndCountView(reviewId, null);
        mvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].viewCount").value(1));
    }

    @Test
    void viewsDoNotChangeReviewVersionOrTimestampAndSurviveConcurrentEdit() throws Exception {
        Review before = reviews.findById(reviewId).orElseThrow();
        service.findByIdAndCountView(reviewId, null);
        Review after = reviews.findById(reviewId).orElseThrow();
        assertThat(after.getVersion()).isEqualTo(before.getVersion());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());

        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch viewed = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> edit = executor.submit(() -> transactions.executeWithoutResult(status -> {
                Review review = reviews.findById(reviewId).orElseThrow();
                review.updateDetails("수정됨", review.getContent(), review.getRating(), review.getProduct(), review.getCategory(), review.getDuration(), review.getPros(), review.getCons(), review.getVerdict());
                loaded.countDown();
                await(viewed);
            }));
            try {
                assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
                service.findByIdAndCountView(reviewId, null);
            } finally {
                viewed.countDown();
            }
            edit.get(10, TimeUnit.SECONDS);
        }
        assertThat(reviews.findById(reviewId).orElseThrow().getTitle()).isEqualTo("수정됨");
        assertThat(views.findCount(reviewId)).isEqualTo(2);
    }

    @Test
    void migrationCreatesTablesForExistingReviewsWithoutChangingThem() throws Exception {
        // 각 테스트가 순차 실행되는 폐기용 DB에서 신규 테이블 생성 경로를 검증합니다.
        jdbc.execute("DROP TABLE review_view_counts");
        jdbc.execute(Files.readString(Path.of("database/review-views.sql")));
        assertThat(service.findByIdEditView(reviewId, author.getId()).id()).isEqualTo(reviewId);
        assertThat(views.findCount(reviewId)).isZero();
        assertThat(service.findByIdAndCountView(reviewId, null).viewCount()).isEqualTo(1);
        reviews.deleteById(reviewId);
        assertThat(views.findCount(reviewId)).isZero();
    }

    @Test
    void allowsCredentialedFrontendRequestsAndRejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/v1/reviews/{id}", reviewId)
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        mvc.perform(options("/api/v1/reviews/{id}", reviewId)
                        .header(HttpHeaders.ORIGIN, "https://other.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    private Long saveReview() {
        return reviews.saveAndFlush(Review.create(new ReviewRequest("조회 테스트", null, textDocument("본문"), BigDecimal.ONE,
                null, null, null, null, null, null, null),
                author, category)).getId();
    }


    private void runConcurrent(int requestCount) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(requestCount)) {
            List<Future<?>> requests = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                requests.add(executor.submit(() -> {
                    await(start);
                    service.findByIdAndCountView(reviewId, null);
                }));
            }
            start.countDown();
            for (Future<?> request : requests) {
                request.get(30, TimeUnit.SECONDS);
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent request");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}

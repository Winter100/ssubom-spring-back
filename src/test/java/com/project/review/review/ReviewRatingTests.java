package com.project.review.review;

import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.repository.ReviewViewRepository;
import com.project.review.storage.entities.ImageUploadStatus;
import com.project.review.storage.entities.UploadStatus;
import com.project.review.storage.repository.ImageUploadRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static com.project.review.review.ReviewContentFixtures.textDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 요청 역직렬화, 범위 검증, 실제 저장 및 조회 응답의 소수 점수를 검증합니다. */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:rating-tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
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
class ReviewRatingTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MemberRepository members;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private ReviewRepository reviews;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ImageUploadRepository uploads;
    @MockitoBean
    private S3Client s3Client;
    @MockitoBean
    private ReviewViewRepository views;

    private Long memberId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        memberId = members.save(Member.create("rating@example.com", "rating-user", "unused-in-this-test")).getId();
        categoryId = categories.save(new Category("평점 테스트")).getId();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1.5", "3.5", "4.25", "5"})
    void acceptsAndPersistsRatingsWithinRange(String rating) throws Exception {
        mvc.perform(post("/api/v1/reviews")
                        .with(jwt().jwt(token -> token.subject(memberId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(rating)))
                .andExpect(status().isCreated());

        reviews.flush();
        entityManager.clear();
        assertThat(reviews.findAll()).singleElement()
                .satisfies(review -> assertThat(review.getRating()).isEqualByComparingTo(rating));
        mvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rating").value(new BigDecimal(rating).doubleValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsBlankMainImageKey(String key) throws Exception {
        mvc.perform(post("/api/v1/reviews")
                        .with(jwt().jwt(token -> token.subject(memberId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewRequest(
                                "이미지 검증", "상품", textDocument("선택 이미지도 빈 키는 허용하지 않습니다."), BigDecimal.ONE,
                                categoryId, key, List.of(), null, null, null, null))))
                .andExpect(status().isBadRequest());
        assertThat(reviews.count()).isZero();
    }

    @Test
    void returnsImageKeysWithEstablishedFieldNames() throws Exception {
        Member member = members.findById(memberId).orElseThrow();
        for (String key : List.of("reviews/main.png", "reviews/sub.png")) {
            uploads.save(new ImageUploadStatus(member, key, "image/png", 100L, UploadStatus.PENDING,
                    Instant.now().plusSeconds(300), Instant.now().plusSeconds(3600)));
        }
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
                .thenReturn(HeadObjectResponse.builder().contentLength(100L).contentType("image/png").build());
        mvc.perform(post("/api/v1/reviews")
                        .with(jwt().jwt(token -> token.subject(memberId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewRequest(
                                "이미지 응답", "상품", textDocument("공개 URL 필드와 저장 키를 구분합니다."), BigDecimal.ONE,
                                categoryId, "reviews/main.png", List.of("reviews/sub.png"), null, null, null, null))))
                .andExpect(status().isCreated());
        reviews.flush();
        entityManager.clear();
        Long reviewId = reviews.findAll().getFirst().getId();
        mvc.perform(get("/api/v1/reviews/{id}", reviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainImageKey").value("reviews/main.png"))
                .andExpect(jsonPath("$.subImageKeys").doesNotExist());
        assertThat(reviews.findById(reviewId).orElseThrow().getImages())
                .extracting(image -> image.getImageKey())
                .containsExactly("reviews/main.png", "reviews/sub.png");
    }

    @Test
    void authorCanDeleteReviewAndImageRows() throws Exception {
        Long reviewId = saveReview();
        mvc.perform(delete("/api/v1/reviews/{id}", reviewId)
                        .with(jwt().jwt(token -> token.subject(memberId.toString()))))
                .andExpect(status().isNoContent());
        reviews.flush();
        entityManager.clear();
        assertThat(reviews.existsById(reviewId)).isFalse();
        assertThat(entityManager.createQuery("select count(i) from ReviewImage i", Long.class)
                .getSingleResult()).isZero();
    }

    @Test
    void anotherMemberCannotDeleteReview() throws Exception {
        Long reviewId = saveReview();
        mvc.perform(delete("/api/v1/reviews/{id}", reviewId)
                        .with(jwt().jwt(token -> token.subject(Long.toString(memberId + 1)))))
                .andExpect(status().isForbidden());
        assertThat(reviews.existsById(reviewId)).isTrue();
    }

    @Test
    void anonymousCannotDeleteReview() throws Exception {
        Long reviewId = saveReview();
        mvc.perform(delete("/api/v1/reviews/{id}", reviewId))
                .andExpect(status().isUnauthorized());
        assertThat(reviews.existsById(reviewId)).isTrue();
    }

    @Test
    void missingReviewCannotBeDeleted() throws Exception {
        mvc.perform(delete("/api/v1/reviews/{id}", Long.MAX_VALUE)
                        .with(jwt().jwt(token -> token.subject(memberId.toString()))))
                .andExpect(status().isNotFound());
    }

    private Long saveReview() {
        Review review = Review.create(new ReviewRequest("삭제 테스트", "test product", textDocument("작성자 권한을 확인합니다."), BigDecimal.ONE,
                null, null, null, null, null, null, null),
                members.findById(memberId).orElseThrow(), categories.findById(categoryId).orElseThrow());
        review.addImage("reviews/main.png", 0);
        return reviews.saveAndFlush(review).getId();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "5.1", "null"})
    void rejectsOutOfRangeOrMissingRating(String rating) throws Exception {
        mvc.perform(post("/api/v1/reviews")
                        .with(jwt().jwt(token -> token.subject(memberId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(rating)))
                .andExpect(status().isBadRequest());

        assertThat(reviews.count()).isZero();
    }

    private String requestBody(String rating) {
        return """
                {"product":"test product","title":"평점 테스트","content":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"소수 평점 저장 확인"}]}]},"rating":%s,"categoryId":%d}
                """.formatted(rating, categoryId);
    }
}

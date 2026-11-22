package com.project.review.review;

import com.project.review.auth.entities.Role;
import com.project.review.auth.service.TokenService;
import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.comment.entities.Comment;
import com.project.review.comment.repository.CommentRepository;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.entities.ImageDeleteStatus;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewLike;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import com.project.review.review.repository.ReviewLikeRepository;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.repository.ReviewViewRepository;
import com.project.review.review.service.ImageDeletionTime;
import com.project.review.review.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:admin-reviews;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "security.app.frontend-origin=http://localhost:3000",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.access-token-expires-minutes=15", "security.jwt.refresh-token-expires-days=7",
        "storage.endpoint=https://example.com", "storage.region=auto", "storage.bucket=test-bucket",
        "storage.access-key=test-access-key", "storage.secret-key=test-secret-key",
        "storage.public-base-url=https://images.example.com", "storage.image-cleanup.enabled=false"
})
@AutoConfigureMockMvc
class AdminReviewTests {
    private static final String ADMIN_PATH = "/api/v1/admin/reviews";

    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;
    @Autowired ReviewService service;
    @Autowired ReviewRepository reviews;
    @Autowired CommentRepository comments;
    @Autowired ReviewLikeRepository likes;
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock imageDeletionClock;
    @MockitoBean ReviewViewRepository views;
    @MockitoSpyBean ReviewImageDeleteRepository deletions;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private Member admin;
    private Member owner;
    private Category category;
    private Long reviewId;

    @BeforeEach
    void setUp() {
        reset(deletions);
        transactions.executeWithoutResult(status -> {
            comments.findAll().stream().map(comment -> comment.getReview().getId()).distinct().forEach(id -> {
                comments.deleteRepliesByReviewId(id);
                comments.deleteAllByReviewId(id);
            });
            likes.deleteAll();
            reviews.deleteAll();
            deletions.deleteAll();
            members.deleteAll();
            categories.deleteAll();
        });
        admin = new Member("admin@test.com", "admin", "unused");
        ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
        admin = members.saveAndFlush(admin);
        owner = members.saveAndFlush(new Member("owner@test.com", "owner", "unused"));
        category = categories.saveAndFlush(new Category("category"));
        reviewId = saveReview("first review", category, true);
        when(views.findCounts(any())).thenReturn(Map.of(reviewId, 12L));
    }

    @ParameterizedTest
    @CsvSource({"false,401", "true,403"})
    void rejectsNonAdminForBothEndpoints(boolean authenticated, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder list = get(ADMIN_PATH);
        MockHttpServletRequestBuilder remove = delete(ADMIN_PATH + "/" + reviewId);
        if (authenticated) {
            list.header("Authorization", bearer(owner));
            remove.header("Authorization", bearer(owner));
        }
        mvc.perform(list).andExpect(status().is(expectedStatus));
        mvc.perform(remove).andExpect(status().is(expectedStatus));
        assertThat(reviews.existsById(reviewId)).isTrue();
        assertThat(deletions.count()).isZero();
        verifyNoInteractions(views);
    }

    @Test
    void rejectsInvalidTokenForBothEndpoints() throws Exception {
        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(delete(ADMIN_PATH + "/" + reviewId).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        assertThat(reviews.existsById(reviewId)).isTrue();
    }

    @Test
    void signedAdminTokenReturnsSameDefaultResponseAndPersonalStats() throws Exception {
        likes.saveAndFlush(new ReviewLike(reviews.findById(reviewId).orElseThrow(), admin));
        String ordinary = mvc.perform(get("/api/v1/reviews").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String administrative = mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.content[0].viewCount").value(12))
                .andExpect(jsonPath("$.content[0].isLike").value(true))
                .andExpect(jsonPath("$.content[0].likeCount").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(administrative)).isEqualTo(mapper.readTree(ordinary));
        mvc.perform(get("/api/v1/reviews")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"title,review,1", "title,review,2", "author,owner,1", "author,own,1", "title,absent,1"})
    void searchAndPaginationMatchOrdinaryList(String searchType, String query, String page) throws Exception {
        saveReview("second review", category, false);
        saveReview("third review", categories.saveAndFlush(new Category("other category")), false);
        String ordinary = mvc.perform(search("/api/v1/reviews", searchType, query, page))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String administrative = mvc.perform(search(ADMIN_PATH, searchType, query, page))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(administrative)).isEqualTo(mapper.readTree(ordinary));
    }

    @Test
    void findsOnlyTheRequestedReviewWhenTitlesMatch() throws Exception {
        saveReview("first review", category, false);
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin))
                        .param("reviewId", reviewId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(reviewId));
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin))
                        .param("reviewId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,0", "size,101", "searchType,invalid", "reviewId,0"})
    void rejectsInvalidListConditions(String parameter, String value) throws Exception {
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin)).param(parameter, value))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesOtherAuthorsReviewAndSchedulesAllImages() throws Exception {
        scheduleWithdrawal(owner);
        addCommentsAndLike();
        Instant before = ImageDeletionTime.nextMidnight(imageDeletionClock.instant());
        mvc.perform(delete(ADMIN_PATH + "/" + reviewId).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        Instant after = ImageDeletionTime.nextMidnight(imageDeletionClock.instant());
        assertThat(reviews.existsById(reviewId)).isFalse();
        assertThat(comments.count()).isZero();
        assertThat(likes.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reviews_images where review_id = ?", Long.class, reviewId))
                .isZero();
        assertThat(deletions.findAll()).extracting(deletion -> deletion.getImageKey())
                .containsExactlyInAnyOrder("cover", "sub", "reviews/body");
        assertThat(deletions.findAll()).allSatisfy(deletion -> {
            assertThat(deletion.getStatus()).isEqualTo(ImageDeleteStatus.PENDING);
            assertThat(deletion.getNextAttemptAt()).isIn(before, after);
        });
    }

    @Test
    void missingReviewReturnsNotFound() throws Exception {
        mvc.perform(delete(ADMIN_PATH + "/" + Long.MAX_VALUE).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        assertThat(deletions.count()).isZero();
    }

    @Test
    void pendingWithdrawalBlocksAdminDeletionButAllowsList() throws Exception {
        scheduleWithdrawal(admin);
        mvc.perform(delete(ADMIN_PATH + "/" + reviewId).header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin))).andExpect(status().isOk());
        assertThat(reviews.existsById(reviewId)).isTrue();
        assertThat(deletions.count()).isZero();
    }

    @Test
    void imageSchedulingFailureRollsBackAllDeletion() throws Exception {
        addCommentsAndLike();
        doThrow(new DataIntegrityViolationException("scheduling failed")).when(deletions).saveAll(any());
        mvc.perform(delete(ADMIN_PATH + "/" + reviewId).header("Authorization", bearer(admin)))
                .andExpect(status().isInternalServerError());
        assertThat(reviews.findById(reviewId).orElseThrow().getImages()).hasSize(2);
        assertThat(comments.count()).isEqualTo(2);
        assertThat(likes.count()).isEqualTo(1);
        assertThat(deletions.count()).isZero();
    }

    @Test
    @WithMockUser(roles = "USER")
    void serviceAlsoRejectsNonAdmin() {
        assertThatThrownBy(() -> service.removeByAdmin(reviewId, owner.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(reviews.existsById(reviewId)).isTrue();
    }

    @Test
    void ordinaryDeleteStillRequiresAuthorAndOwnerCanDelete() throws Exception {
        mvc.perform(delete("/api/v1/reviews/" + reviewId).header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
        assertThat(reviews.existsById(reviewId)).isTrue();
        mvc.perform(delete("/api/v1/reviews/" + reviewId).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        assertThat(reviews.existsById(reviewId)).isFalse();
        assertThat(deletions.count()).isEqualTo(3);
    }

    private MockHttpServletRequestBuilder search(String path, String searchType, String query, String page) {
        return get(path).header("Authorization", bearer(admin)).param("searchType", searchType)
                .param("query", query).param("categoryId", category.getId().toString())
                .param("page", page).param("size", "1");
    }

    private String bearer(Member member) {
        return "Bearer " + tokens.createAccessToken(member);
    }

    private Long saveReview(String title, Category reviewCategory, boolean withImages) {
        Review review = BeanUtils.instantiateClass(Review.class);
        review.updateDetails(title, mapper.readTree(withImages
                        ? "{\"type\":\"doc\",\"content\":[{\"type\":\"image\",\"attrs\":{\"src\":\"https://images.example.com/reviews/body\"}}]}"
                        : "{\"type\":\"doc\",\"content\":[]}"),
                BigDecimal.ONE, "product", reviewCategory, null, List.of(), List.of(), null);
        ReflectionTestUtils.setField(review, "author", owner);
        if (withImages) {
            review.addImage("cover", 0);
            review.addImage("sub", 1);
        }
        return reviews.saveAndFlush(review).getId();
    }

    private void scheduleWithdrawal(Member member) {
        member.requestWithdrawal(Instant.now(), Instant.now().plusSeconds(86400));
        members.saveAndFlush(member);
    }

    private void addCommentsAndLike() {
        Review review = reviews.findById(reviewId).orElseThrow();
        Comment root = comments.saveAndFlush(new Comment(review, owner, null, "root"));
        comments.saveAndFlush(new Comment(review, admin, root, "reply"));
        likes.saveAndFlush(new ReviewLike(review, admin));
    }
}

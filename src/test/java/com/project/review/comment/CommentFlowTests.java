package com.project.review.comment;

import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.comment.dtos.CommentCreateRequest;
import com.project.review.comment.dtos.CommentUpdateRequest;
import com.project.review.comment.entities.Comment;
import com.project.review.comment.repository.CommentRepository;
import com.project.review.comment.service.CommentService;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:comment-flow;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.access-token-expires-minutes=15", "security.jwt.refresh-token-expires-days=7",
        "storage.endpoint=https://example.com", "storage.region=auto", "storage.bucket=test-bucket",
        "storage.access-key=test-access-key", "storage.secret-key=test-secret-key",
        "storage.public-base-url=https://images.example.com"
})
@AutoConfigureMockMvc
class CommentFlowTests {
    @Autowired MockMvc mvc;
    @Autowired CommentRepository comments;
    @Autowired ReviewRepository reviews;
    @Autowired ReviewImageDeleteRepository imageDeletions;
    @Autowired CommentService commentService;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired TransactionTemplate transactions;
    @MockitoBean S3Client s3;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private Member owner;
    private Member other;
    private Long reviewId;
    private Long otherReviewId;

    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(status -> {
            List<Long> ids = comments.findAll().stream().map(c -> c.getReview().getId()).distinct().toList();
            ids.forEach(id -> {
                comments.deleteRepliesByReviewId(id);
                comments.deleteAllByReviewId(id);
            });
        });
        reviews.deleteAll();
        imageDeletions.deleteAll();
        members.deleteAll();
        categories.deleteAll();
        owner = members.save(new Member("owner@test.com", "owner", "unused"));
        other = members.save(new Member("other@test.com", "other", "unused"));
        Category category = categories.save(new Category("test"));
        reviewId = saveReview(category);
        otherReviewId = saveReview(category);
    }

    private Long saveReview(Category category) {
        Review review = BeanUtils.instantiateClass(Review.class);
        review.updateDetails("review", mapper.readTree("{\"type\":\"doc\",\"content\":[]}"),
                BigDecimal.ONE, "product", category, null, List.of(), List.of(), null);
        ReflectionTestUtils.setField(review, "author", owner);
        return reviews.saveAndFlush(review).getId();
    }

    private String path() {
        return "/api/v1/reviews/" + reviewId + "/comments";
    }

    private Long create(Long target, String content) throws Exception {
        String response = mvc.perform(post(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CommentCreateRequest(content, target))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.author.nickname").value("owner"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("id").longValue();
    }

    @Test
    void flattensRepeatedRepliesAndPagesOnlyRoots() throws Exception {
        Long root = create(null, "root");
        Long reply = create(root, "reply");
        Long next = create(reply, "next");
        create(next, "last");
        create(null, "second root");
        mvc.perform(get(path()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].replies.length()").value(3))
                .andExpect(jsonPath("$.content[0].replies[2].content").value("last"));
        transactions.executeWithoutResult(status -> assertThat(comments.findAll().stream()
                .filter(c -> c.getParent() != null).map(c -> c.getParent().getId())).containsOnly(root));
        mvc.perform(get(path()).param("page", "2").param("size", "1"))
                .andExpect(jsonPath("$.content[0].content").value("second root"));
    }

    @Test
    void deletesPersonalDataAndAllowsRepliesToDeletedComments() throws Exception {
        Long root = create(null, "secret");
        Long reply = create(root, "reply secret");
        var createdAt = comments.findById(root).orElseThrow().getCreatedAt();
        for (Long id : List.of(root, reply)) {
            mvc.perform(delete(path() + "/" + id).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                    .andExpect(status().isNoContent());
            mvc.perform(put(path() + "/" + id).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"restore\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(delete(path() + "/" + id).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                    .andExpect(status().isConflict());
        }
        create(root, "after root deletion");
        create(reply, "after reply deletion");
        Comment deleted = comments.findById(root).orElseThrow();
        assertThat(deleted.getAuthor()).isNull();
        assertThat(deleted.getContent()).isEqualTo("삭제된 댓글입니다");
        assertThat(deleted.getCreatedAt()).isEqualTo(createdAt);
        mvc.perform(get(path())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].author").doesNotExist())
                .andExpect(jsonPath("$.content[0].deleted").value(true))
                .andExpect(jsonPath("$.content[0].replies.length()").value(3));
    }

    @Test
    void enforcesOwnershipAndAnonymousWriteProtection() throws Exception {
        Long root = create(null, "original");
        mvc.perform(put(path() + "/" + root).with(jwt().jwt(j -> j.subject(other.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"edited\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path() + "/" + root).with(jwt().jwt(j -> j.subject(other.getId().toString()))))
                .andExpect(status().isForbidden());
        mvc.perform(put(path() + "/" + root).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"edited\"}"))
                .andExpect(status().isNoContent());
        assertThat(comments.findById(root).orElseThrow().getContent()).isEqualTo("edited");
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(path() + "/" + root).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(path() + "/" + root)).andExpect(status().isUnauthorized());
    }

    @Test
    void validatesContentAndReviewMembership() throws Exception {
        for (String content : List.of("", "   ", "a".repeat(1001))) {
            mvc.perform(post(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new CommentCreateRequest(content, null))))
                    .andExpect(status().isBadRequest());
        }
        Long root = create(null, "a".repeat(1000));
        String foreignPath = "/api/v1/reviews/" + otherReviewId + "/comments";
        mvc.perform(post(foreignPath).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\",\"parentId\":" + root + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(foreignPath + "/" + root).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isNotFound());
        mvc.perform(get(path()).param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(get(path()).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reviews/9223372036854775807/comments")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidUpdatesWithoutChangingStoredContent() throws Exception {
        Long root = create(null, "original");
        for (String content : new String[]{null, "", "   ", "a".repeat(1001)}) {
            mvc.perform(put(path() + "/" + root)
                            .with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(new CommentUpdateRequest(content))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(comments.findById(root).orElseThrow().getContent()).isEqualTo("original");

        String maximumContent = "a".repeat(1000);
        mvc.perform(put(path() + "/" + root)
                        .with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CommentUpdateRequest(maximumContent))))
                .andExpect(status().isNoContent());
        assertThat(comments.findById(root).orElseThrow().getContent()).isEqualTo(maximumContent);
    }

    @Test
    void reviewDeletionRemovesAllCommentsAndRollsBackTogether() throws Exception {
        transactions.executeWithoutResult(status -> reviews.findById(reviewId).orElseThrow().addImage("reviews/comment-test", 0));
        Long root = create(null, "root");
        Long reply = create(root, "reply");
        mvc.perform(delete(path() + "/" + root).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isNoContent());
        transactions.executeWithoutResult(status -> {
            deleteReview();
            reviews.flush();
            status.setRollbackOnly();
        });
        assertThat(comments.existsById(reply)).isTrue();
        assertThat(reviews.existsById(reviewId)).isTrue();
        assertThat(imageDeletions.count()).isZero();
        mvc.perform(delete("/api/v1/reviews/" + reviewId).with(jwt().jwt(j -> j.subject(other.getId().toString()))))
                .andExpect(status().isForbidden());
        assertThat(comments.count()).isEqualTo(2);
        mvc.perform(delete("/api/v1/reviews/" + reviewId).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isNoContent());
        assertThat(comments.count()).isZero();
        assertThat(reviews.existsById(reviewId)).isFalse();
        assertThat(reviews.existsById(otherReviewId)).isTrue();
        assertThat(imageDeletions.findAll()).extracting(image -> image.getImageKey()).containsExactly("reviews/comment-test");
    }

    @Test
    void returnsCurrentNicknameAndProfileImage() throws Exception {
        create(null, "comment");
        transactions.executeWithoutResult(status -> {
            Member member = members.findById(owner.getId()).orElseThrow();
            member.changeName("new nickname", Instant.now());
            member.changeProfileImage("https://images.example.com/avatar.png");
        });
        mvc.perform(get(path())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].author.nickname").value("new nickname"))
                .andExpect(jsonPath("$.content[0].author.profileImage").value("https://images.example.com/avatar.png"));
    }

    @Test
    void listFetchesAuthorsInConstantQueryCount() throws Exception {
        Long first = create(null, "first");
        Long second = create(null, "second");
        create(first, "reply one");
        create(second, "reply two");
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mvc.perform(get(path()).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[1].replies[0].author.nickname").value("owner"));
        // 리뷰 존재 확인, 원댓글, 원댓글 개수, 대댓글의 네 쿼리만 실행합니다.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
    }

    @Test
    void reviewDeletionWaitsForCommentWriteAndRemovesCommittedComment() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch started = new CountDownLatch(1);
            var deletion = transactions.execute(status -> {
                reviews.findByIdForUpdate(reviewId).orElseThrow();
                commentService.create(reviewId, new CommentCreateRequest("concurrent", null), owner.getId());
                var pending = executor.submit(() -> {
                    started.countDown();
                    deleteReview();
                });
                awaitStarted(started);
                assertThatThrownBy(() -> pending.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                return pending;
            });
            deletion.get(10, TimeUnit.SECONDS);
        }
        assertThat(comments.count()).isZero();
        assertThat(reviews.existsById(reviewId)).isFalse();
    }

    @Test
    void commentWriteWaitsForDeletionAndReturnsNotFound() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch started = new CountDownLatch(1);
            var creation = transactions.execute(status -> {
                deleteReview();
                var pending = executor.submit(() -> {
                    started.countDown();
                    try {
                        commentService.create(reviewId, new CommentCreateRequest("late", null), owner.getId());
                        throw new AssertionError("삭제된 리뷰에 댓글이 생성되었습니다.");
                    } catch (BusinessException exception) {
                        return exception.getErrorCode();
                    }
                });
                awaitStarted(started);
                assertThatThrownBy(() -> pending.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                return pending;
            });
            assertThat(creation.get(10, TimeUnit.SECONDS)).isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
        }
        assertThat(comments.count()).isZero();
    }

    private void deleteReview() {
        try {
            mvc.perform(delete("/api/v1/reviews/" + reviewId)
                            .with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                    .andExpect(status().isNoContent());
        } catch (Exception exception) {
            throw new AssertionError("리뷰 삭제 요청에 실패했습니다.", exception);
        }
    }

    private void awaitStarted(CountDownLatch started) {
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}

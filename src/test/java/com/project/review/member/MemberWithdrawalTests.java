package com.project.review.member;

import com.project.review.auth.entities.RefreshToken;
import com.project.review.auth.repository.RefreshTokenRepository;
import com.project.review.auth.service.AuthService;
import com.project.review.auth.service.TokenService;
import com.project.review.auth.dtos.LoginRequest;
import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.comment.entities.Comment;
import com.project.review.comment.repository.CommentRepository;
import com.project.review.comment.service.CommentService;
import com.project.review.comment.dtos.CommentCreateRequest;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.member.service.MemberDeletionService;
import com.project.review.member.service.MemberWithdrawalCleanup;
import com.project.review.member.service.MemberWithdrawalService;
import com.project.review.member.service.MemberService;
import com.project.review.member.dtos.MemberUpdateRequest;
import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewLike;
import com.project.review.review.entities.ReviewImageDelete;
import com.project.review.review.entities.ImageDeleteStatus;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.repository.ReviewLikeRepository;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import com.project.review.review.service.ReviewImageCleanup;
import com.project.review.review.service.ReviewService;
import com.project.review.storage.entities.ImageUploadStatus;
import com.project.review.storage.entities.UploadStatus;
import com.project.review.storage.repository.ImageUploadRepository;
import com.project.review.storage.service.StorageService;
import com.project.review.storage.dtos.ImageUploadBatchRequest;
import com.project.review.storage.dtos.ImageUploadRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import com.project.review.ReviewApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** WITHDRAWAL_TEST_URL은 반드시 폐기 가능한 전용 DB여야 합니다. 스키마를 생성·삭제합니다. */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=${WITHDRAWAL_TEST_URL:jdbc:h2:mem:withdrawals;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${WITHDRAWAL_TEST_USER:sa}",
        "spring.datasource.password=${WITHDRAWAL_TEST_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "security.app.frontend-origin=http://localhost:3000",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.access-token-expires-minutes=15", "security.jwt.refresh-token-expires-days=7",
        "storage.endpoint=https://example.com", "storage.bucket=test-bucket",
        "storage.access-key=test", "storage.secret-key=test", "storage.public-base-url=https://images.example.com"
})
@AutoConfigureMockMvc
class MemberWithdrawalTests {
    private static final Instant NOW = Instant.parse("2026-11-16T06:00:00Z");
    private static final Instant DUE = Instant.parse("2026-11-17T15:00:00Z");
    private static final String PASSWORD = "password123";
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired ReviewRepository reviews;
    @Autowired CommentRepository comments;
    @Autowired ReviewLikeRepository likes;
    @Autowired RefreshTokenRepository tokens;
    @Autowired ReviewImageDeleteRepository imageDeletions;
    @MockitoSpyBean ImageUploadRepository uploads;
    @Autowired MemberWithdrawalService withdrawals;
    @MockitoSpyBean MemberDeletionService deletion;
    @Autowired MemberWithdrawalCleanup cleanup;
    @Autowired ReviewImageCleanup imageCleanup;
    @Autowired ReviewService reviewService;
    @Autowired CommentService commentService;
    @Autowired MemberService memberService;
    @Autowired StorageService storage;
    @Autowired ConfigurableApplicationContext context;
    @Autowired AuthService auth;
    @Autowired TokenService tokenService;
    @Autowired PasswordEncoder passwords;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestClock clock;
    @MockitoBean S3Client s3;
    // 실제 벽시계 스케줄은 실행하지 않고 수동 호출과 시작 이벤트를 검증합니다.
    @MockitoBean TaskScheduler scheduler;
    private Member owner;
    private Member other;
    private Category category;

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
    }

    static class TestClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        void set(Instant value) { now.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    }

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        jdbc.update("delete from comments where parent_id is not null");
        comments.deleteAll();
        likes.deleteAll();
        tokens.deleteAll();
        uploads.deleteAll();
        reviews.deleteAll();
        members.deleteAll();
        categories.deleteAll();
        imageDeletions.deleteAll();
        owner = members.save(Member.create("owner@test.com", "owner", passwords.encode(PASSWORD)));
        other = members.save(Member.create("other@test.com", "other", passwords.encode(PASSWORD)));
        category = categories.save(new Category("withdrawal"));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-16T06:00:00Z,2026-11-17T15:00:00Z",
            "2026-11-15T15:00:00Z,2026-11-17T15:00:00Z",
            "2026-11-30T14:59:59Z,2026-12-01T15:00:00Z",
            "2026-12-31T06:00:00Z,2027-01-01T15:00:00Z"
    })
    void schedulesAtMidnightAfterThe24HourDate(String requested, String expected) {
        clock.set(Instant.parse(requested));
        var result = withdrawals.request(owner.getId(), PASSWORD);
        assertThat(result.requestedAt()).isEqualTo(Instant.parse(requested));
        assertThat(result.scheduledAt()).isEqualTo(Instant.parse(expected));
    }

    @Test
    void duplicateRequestDoesNotExtendScheduleAndCancelAllowsNewRequest() {
        var first = withdrawals.request(owner.getId(), PASSWORD);
        clock.set(NOW.plusSeconds(3600));
        assertThat(withdrawals.request(owner.getId(), PASSWORD)).isEqualTo(first);
        withdrawals.cancel(owner.getId());
        withdrawals.cancel(owner.getId());
        assertThat(withdrawals.find(owner.getId()).scheduledAt()).isNull();
        assertThat(withdrawals.find(owner.getId()).requestedAt()).isNull();
        assertThat(withdrawals.request(owner.getId(), PASSWORD).requestedAt()).isEqualTo(clock.instant());
    }

    @Test
    void wrongPasswordAlsoRejectsDuplicateRequest() {
        withdrawals.request(owner.getId(), PASSWORD);
        assertThatThrownBy(() -> withdrawals.request(owner.getId(), "wrong"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.WITHDRAWAL_PASSWORD_MISMATCH));
        assertThat(withdrawals.find(owner.getId()).scheduledAt()).isEqualTo(DUE);
    }

    @Test
    void apiRequiresAuthenticationAndValidPasswordAndReturnsUncachedStatus() throws Exception {
        mvc.perform(post("/api/v1/auth/withdrawal").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"password123\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/withdrawal")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/auth/withdrawal")).andExpect(status().isUnauthorized());
        String bearer = bearer();
        mvc.perform(get("/api/v1/auth/withdrawal").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestedAt").isEmpty())
                .andExpect(jsonPath("$.scheduledAt").isEmpty());
        mvc.perform(post("/api/v1/auth/withdrawal").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/withdrawal").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("현재 비밀번호가 일치하지 않습니다."));
        mvc.perform(post("/api/v1/auth/withdrawal").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.scheduledAt").value(DUE.toString()));
        mvc.perform(get("/api/v1/auth/withdrawal").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.requestedAt").value(NOW.toString()));
        mvc.perform(delete("/api/v1/auth/withdrawal").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
    }

    @Test
    void pendingMemberCanLoginAndRefreshButCannotWriteUntilCancellation() {
        withdrawals.request(owner.getId(), PASSWORD);
        var login = auth.login(new LoginRequest(owner.getEmail(), PASSWORD));
        assertThat(auth.refreshAccessToken(login.refreshToken()).accessToken()).isNotBlank();
        assertThatThrownBy(() -> reviewService.create(request(), owner.getId()))
                .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.WITHDRAWAL_CONTENT_WRITE_NOT_ALLOWED.getMessage());
        assertThat(reviews.count()).isZero();
        assertThat(withdrawals.find(owner.getId()).scheduledAt()).isEqualTo(DUE);
        withdrawals.cancel(owner.getId());
        reviewService.create(request(), owner.getId());
        assertThat(reviews.count()).isEqualTo(1);
    }

    @Test
    void neverDeletesBeforeDueAndAllowsCancellationAfterDue() {
        withdrawals.request(owner.getId(), PASSWORD);
        clock.set(DUE.minusSeconds(1));
        cleanup.deleteDueMembers();
        assertThat(members.existsById(owner.getId())).isTrue();
        clock.set(DUE.plusSeconds(60));
        withdrawals.cancel(owner.getId());
        cleanup.deleteDueMembers();
        assertThat(members.existsById(owner.getId())).isTrue();
    }

    @Test
    void deletesMemberAndReviewsButAnonymizesCommentsOnSurvivingReviews() {
        Review owned = saveReview(owner);
        Review surviving = saveReview(other);
        comments.save(new Comment(owned, other, null, "다른 회원의 댓글"));
        Comment root = comments.save(new Comment(surviving, owner, null, "개인 내용"));
        Comment reply = comments.save(new Comment(surviving, other, root, "남아야 할 답글"));
        likes.save(new ReviewLike(owned, other));
        likes.save(new ReviewLike(surviving, owner));
        likes.save(new ReviewLike(surviving, other));
        jdbc.update("insert into review_view_counts (review_id, view_count) values (?, 10)", owned.getId());
        tokens.save(new RefreshToken(owner, "test-hash", NOW.plusSeconds(86400)));
        upload("reviews/unused", UploadStatus.PENDING);
        withdrawals.request(owner.getId(), PASSWORD);
        clock.set(DUE);
        cleanup.deleteDueMembers();

        assertThat(members.existsById(owner.getId())).isFalse();
        assertThat(reviews.existsById(owned.getId())).isFalse();
        assertThat(reviews.existsById(surviving.getId())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from review_view_counts where review_id = ?", Long.class, owned.getId())).isZero();
        assertThat(comments.count()).isEqualTo(2);
        Comment deleted = comments.findById(root.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getAuthor()).isNull();
        assertThat(deleted.getContent()).isEqualTo("삭제된 댓글입니다");
        assertThat(comments.findById(reply.getId()).orElseThrow().getContent()).isEqualTo("남아야 할 답글");
        assertThat(likes.count()).isEqualTo(1);
        assertThat(tokens.count()).isZero();
        assertThat(uploads.count()).isZero();
        assertThat(members.existsById(other.getId())).isTrue();
    }

    @Test
    void deduplicatesAllImagesAndDefersStorageDeletionUntilNextMidnight() {
        Review owned = saveReview(owner);
        transactions.executeWithoutResult(status -> {
            Review current = reviews.findById(owned.getId()).orElseThrow();
            current.addImage("reviews/main", 0);
            current.addImage("reviews/gallery", 1);
            members.findById(owner.getId()).orElseThrow().changeProfileImage("reviews/profile");
        });
        for (String key : List.of("main", "gallery", "body", "profile", "unused")) {
            upload("reviews/" + key, UploadStatus.ATTACHED);
        }
        withdrawals.request(owner.getId(), PASSWORD);
        clock.set(DUE);
        cleanup.deleteDueMembers();
        assertThat(imageDeletions.findAll()).extracting(ReviewImageDelete::getImageKey)
                .containsExactlyInAnyOrder("reviews/main", "reviews/gallery", "reviews/body", "reviews/profile", "reviews/unused");
        assertThat(imageDeletions.findAll()).allSatisfy(row ->
                assertThat(row.getNextAttemptAt()).isEqualTo(DUE.plusSeconds(86400)));
        imageCleanup.deleteUnusedImages();
        verifyNoInteractions(s3);
        clock.set(DUE.plusSeconds(86400));
        when(s3.deleteObjects(org.mockito.ArgumentMatchers.<java.util.function.Consumer<DeleteObjectsRequest.Builder>>any()))
                .thenReturn(DeleteObjectsResponse.builder().build());
        imageCleanup.deleteUnusedImages();
        assertThat(imageDeletions.findAll()).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(ImageDeleteStatus.DELETED));
    }

    @Test
    void protectsProfileImagesStillInUse() {
        transactions.executeWithoutResult(status -> members.findById(other.getId()).orElseThrow()
                .changeProfileImage("reviews/active-profile"));
        var row = imageDeletions.save(new ReviewImageDelete("reviews/active-profile", NOW));
        imageCleanup.deleteUnusedImages();
        verifyNoInteractions(s3);
        assertThat(imageDeletions.findById(row.getId()).orElseThrow().getStatus()).isEqualTo(ImageDeleteStatus.PENDING);
    }

    @Test
    void failedDeletionRollsBackReviewsCommentsAndQueueAndDoesNotBlockOtherMembers() {
        Review owned = saveReview(owner);
        Review surviving = saveReview(other);
        Comment comment = comments.save(new Comment(surviving, owner, null, "원본"));
        withdrawals.request(owner.getId(), PASSWORD);
        withdrawals.request(other.getId(), PASSWORD);
        clock.set(DUE);
        doThrow(new IllegalStateException("injected failure")).when(uploads).deleteAllByMemberId(owner.getId());
        cleanup.deleteDueMembers();
        assertThat(members.existsById(owner.getId())).isTrue();
        assertThat(reviews.existsById(owned.getId())).isTrue();
        // 다른 회원의 삭제를 막지 않으므로 그 리뷰와 댓글은 함께 삭제됩니다.
        assertThat(members.existsById(other.getId())).isFalse();
        assertThat(comments.existsById(comment.getId())).isFalse();
        assertThat(imageDeletions.count()).isEqualTo(1);
        reset(uploads);
        cleanup.deleteDueMembers();
        assertThat(members.existsById(owner.getId())).isFalse();
    }

    @Test
    void rollbackKeepsAnonymizedCommentAndAllDependentsUnchanged() {
        Review owned = saveReview(owner);
        Review surviving = saveReview(other);
        Comment comment = comments.save(new Comment(surviving, owner, null, "원본"));
        tokens.save(new RefreshToken(owner, "hash", NOW.plusSeconds(3600)));
        upload("reviews/unused", UploadStatus.PENDING);
        withdrawals.request(owner.getId(), PASSWORD);
        clock.set(DUE);
        doThrow(new IllegalStateException("injected failure")).when(uploads).deleteAllByMemberId(owner.getId());
        assertThatThrownBy(() -> deletion.deleteIfDue(owner.getId(), DUE)).isInstanceOf(RuntimeException.class);
        assertThat(comments.findById(comment.getId()).orElseThrow().isDeleted()).isFalse();
        assertThat(comments.findById(comment.getId()).orElseThrow().getContent()).isEqualTo("원본");
        assertThat(reviews.existsById(owned.getId())).isTrue();
        assertThat(tokens.count()).isEqualTo(1);
        assertThat(uploads.count()).isEqualTo(1);
        assertThat(imageDeletions.count()).isZero();
    }

    @Test
    void deletionInvalidatesExistingAccessAndRefreshTokens() throws Exception {
        var login = auth.login(new LoginRequest(owner.getEmail(), PASSWORD));
        withdrawals.request(owner.getId(), PASSWORD);
        clock.set(DUE);
        cleanup.deleteDueMembers();
        mvc.perform(get("/api/v1/member").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refreshAccessToken(login.refreshToken())).isInstanceOf(BusinessException.class);
        assertThat(deletion.deleteIfDue(owner.getId(), DUE)).isFalse();
    }

    @Test
    void cancellationWinningTheLockPreventsDeletion() throws Exception {
        withdrawals.request(owner.getId(), PASSWORD);
        clock.set(DUE);
        CountDownLatch cancelled = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cancel = executor.submit(() -> transactions.executeWithoutResult(status -> {
                withdrawals.cancel(owner.getId());
                cancelled.countDown();
                await(release);
            }));
            try {
                assertThat(cancelled.await(10, TimeUnit.SECONDS)).isTrue();
                var deleting = executor.submit(() -> deletion.deleteIfDue(owner.getId(), DUE));
                assertThatThrownBy(() -> deleting.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                cancel.get(10, TimeUnit.SECONDS);
                assertThat(deleting.get(10, TimeUnit.SECONDS)).isFalse();
            } finally { release.countDown(); }
        }
        assertThat(members.existsById(owner.getId())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"comment", "review", "upload", "login", "profile"})
    void deletionWinningTheLockRejectsNewWritesAndCancellation(String operation) throws Exception {
        Review surviving = saveReview(other);
        withdrawals.request(owner.getId(), PASSWORD);
        clock.set(DUE);
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var deleting = executor.submit(() -> transactions.executeWithoutResult(status -> {
                deletion.deleteIfDue(owner.getId(), DUE);
                deleted.countDown();
                await(release);
            }));
            try {
                assertThat(deleted.await(10, TimeUnit.SECONDS)).isTrue();
                var writing = executor.submit(() -> {
                    switch (operation) {
                        case "comment" -> commentService.create(surviving.getId(),
                                new CommentCreateRequest("늦은 댓글", null), owner.getId());
                        case "review" -> reviewService.create(request(), owner.getId());
                        case "upload" -> storage.uploadUrls(new ImageUploadBatchRequest(List.of(
                                new ImageUploadRequest("test.png", "image/png", 10L, "client"))), owner);
                        case "login" -> auth.login(new LoginRequest(owner.getEmail(), PASSWORD));
                        case "profile" -> memberService.updateMe(owner.getId(), new MemberUpdateRequest("changed", null));
                        default -> throw new IllegalArgumentException(operation);
                    }
                });
                var cancelling = executor.submit(() -> withdrawals.cancel(owner.getId()));
                assertThatThrownBy(() -> writing.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                deleting.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(() -> writing.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
                assertThatThrownBy(() -> cancelling.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
            } finally { release.countDown(); }
        }
        assertThat(comments.count()).isZero();
        assertThat(reviews.count()).isEqualTo(1);
        assertThat(uploads.count()).isZero();
        assertThat(tokens.count()).isZero();
    }

    @Test
    void twoWorkersCannotDeleteTheSameMemberTwice() throws Exception {
        saveReview(owner);
        withdrawals.request(owner.getId(), PASSWORD);
        clock.set(DUE);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> deletion.deleteIfDue(owner.getId(), DUE));
            var second = executor.submit(() -> deletion.deleteIfDue(owner.getId(), DUE));
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(imageDeletions.count()).isEqualTo(1);
    }

    @Test
    void startupRecoversOverdueMembersAcrossMultiplePages() {
        List<Member> dueMembers = new ArrayList<>();
        for (int index = 0; index < 105; index++) {
            Member member = Member.create("due" + index + "@test.com", "due" + index, owner.getPassword());
            member.requestWithdrawal(NOW, DUE);
            dueMembers.add(member);
        }
        members.saveAll(dueMembers);
        clock.set(DUE.plusSeconds(600));
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(ReviewApplication.class),
                new String[0], context, Duration.ZERO));
        assertThat(members.count()).isEqualTo(2);
        assertThat(members.findDueWithdrawalIds(clock.instant(), 0L,
                org.springframework.data.domain.PageRequest.of(0, 100))).isEmpty();
    }

    @Test
    void migrationIsRepeatableWithoutChangingExistingMembers() throws Exception {
        // 실행 중인 JPA 스키마 대신 예약 컬럼이 없는 이전 회원 테이블을 별도로 재현합니다.
        String sql = Files.readString(Path.of("database/member-withdrawal.sql"))
                .replace("members", "withdrawal_migration_members");
        jdbc.execute("create table withdrawal_migration_members as select id, email, nickname from members");
        try {
            transactions.executeWithoutResult(status -> {
                for (int attempt = 0; attempt < 2; attempt++) {
                    for (String statement : sql.replace("BEGIN;", "").replace("COMMIT;", "").split(";")) {
                        if (!statement.isBlank()) jdbc.execute(statement);
                    }
                }
            });
            assertThat(jdbc.queryForObject("select email from withdrawal_migration_members where id = ?",
                    String.class, owner.getId())).isEqualTo(owner.getEmail());
            assertThat(jdbc.queryForObject("select count(*) from withdrawal_migration_members "
                    + "where withdrawal_requested_at is null and withdrawal_scheduled_at is null", Long.class)).isEqualTo(2);
        } finally {
            jdbc.execute("drop table withdrawal_migration_members");
        }
    }

    @Test
    void pendingMemberCannotCreateCommentsOrUpdateOrDeleteReviews() throws Exception {
        Review owned = reviews.save(Review.create(request(), owner, category));
        Review surviving = reviews.save(Review.create(request(), other, category));
        var comment = commentService.create(surviving.getId(), new CommentCreateRequest("existing", null), owner.getId());
        withdrawals.request(owner.getId(), PASSWORD);
        var update = new com.project.review.review.dtos.ReviewUpdateRequest(
                "changed", request().content(), BigDecimal.valueOf(3), "product", category.getId(),
                null, List.of(), null, List.of(), List.of(), null);
        assertThatThrownBy(() -> reviewService.update(owned.getId(), update, owner.getId()))
                .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.WITHDRAWAL_CONTENT_WRITE_NOT_ALLOWED.getMessage());
        assertThatThrownBy(() -> reviewService.remove(owned.getId(), owner.getId()))
                .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.WITHDRAWAL_CONTENT_WRITE_NOT_ALLOWED.getMessage());
        for (Long parentId : java.util.Arrays.asList(null, comment.id())) {
            assertThatThrownBy(() -> commentService.create(surviving.getId(), new CommentCreateRequest("blocked", parentId), owner.getId()))
                    .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.WITHDRAWAL_CONTENT_WRITE_NOT_ALLOWED.getMessage());
        }
        mvc.perform(post("/api/v1/reviews/{id}/comments", surviving.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"blocked\"}"))
                .andExpect(status().isForbidden());
        assertThat(reviews.findById(owned.getId()).orElseThrow().getTitle()).isEqualTo(request().title());
        assertThat(comments.count()).isEqualTo(1);
        assertThat(imageDeletions.count()).isZero();
        assertThatThrownBy(() -> commentService.update(surviving.getId(), comment.id(),
                new com.project.review.comment.dtos.CommentUpdateRequest("edited"), owner.getId()))
                .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.WITHDRAWAL_CONTENT_WRITE_NOT_ALLOWED.getMessage());
        assertThatThrownBy(() -> commentService.remove(surviving.getId(), comment.id(), owner.getId()))
                .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.WITHDRAWAL_CONTENT_WRITE_NOT_ALLOWED.getMessage());
        mvc.perform(put("/api/v1/reviews/{reviewId}/comments/{commentId}", surviving.getId(), comment.id())
                .header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"edited\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/reviews/{reviewId}/comments/{commentId}", surviving.getId(), comment.id())
                .header(HttpHeaders.AUTHORIZATION, bearer())).andExpect(status().isForbidden());
        assertThat(comments.findById(comment.id()).orElseThrow().getContent()).isEqualTo("existing");
        likes.save(new ReviewLike(surviving, owner));
        assertThatThrownBy(() -> reviewService.insertLike(surviving.getId(), owner.getId()))
                .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.WITHDRAWAL_CONTENT_WRITE_NOT_ALLOWED.getMessage());
        assertThatThrownBy(() -> reviewService.deleteLike(surviving.getId(), owner.getId()))
                .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.WITHDRAWAL_CONTENT_WRITE_NOT_ALLOWED.getMessage());
        assertThat(likes.count()).isEqualTo(1);
        mvc.perform(put("/api/v1/reviews/{id}/like", surviving.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/reviews/{id}/like", surviving.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer())).andExpect(status().isForbidden());
        memberService.updateMe(owner.getId(), new MemberUpdateRequest("changed", null));
        withdrawals.cancel(owner.getId());
        commentService.update(surviving.getId(), comment.id(), new com.project.review.comment.dtos.CommentUpdateRequest("edited"), owner.getId());
        commentService.remove(surviving.getId(), comment.id(), owner.getId());
        reviewService.deleteLike(surviving.getId(), owner.getId());
        assertThat(likes.count()).isZero();
        reviewService.update(owned.getId(), update, owner.getId());
        commentService.create(surviving.getId(), new CommentCreateRequest("allowed", null), owner.getId());
        reviewService.remove(owned.getId(), owner.getId());
        assertThat(reviews.existsById(owned.getId())).isFalse();
    }
    private String bearer() { return "Bearer " + tokenService.createAccessToken(owner); }

    private ReviewRequest request() {
        return requestWithContent("{\"type\":\"doc\",\"content\":[]}");
    }

    private ReviewRequest requestWithContent(String content) {
        return new ReviewRequest("후기", "상품", JsonMapper.builder().build().readTree(
                content),
                BigDecimal.valueOf(4), category.getId(), null, List.of(), null, List.of(), List.of(), null);
    }

    private Review saveReview(Member author) {
        return reviews.save(Review.create(requestWithContent(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"image\",\"attrs\":{\"src\":\"reviews/body\"}}]}"), author, category));
    }

    private void upload(String key, UploadStatus status) {
        uploads.save(new ImageUploadStatus(owner, key, "image/png", 10L, status, NOW.plusSeconds(300), NOW.plusSeconds(3600)));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("latch timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}

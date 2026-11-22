package com.project.review.review;

import com.project.review.auth.entities.Role;
import com.project.review.auth.service.TokenService;
import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.member.service.MemberDeletionService;
import com.project.review.review.dtos.ReviewReportRequest;
import com.project.review.review.dtos.ReviewReportTypeCreateRequest;
import com.project.review.review.dtos.ReviewReportTypeUpdateRequest;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewReport;
import com.project.review.review.entities.ReviewReportType;
import com.project.review.review.repository.ReviewReportTypeRepository;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import com.project.review.review.repository.ReviewReportRepository;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.service.ReviewReportService;
import com.project.review.review.service.ReviewReportTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** REVIEW_REPORT_TEST_URL을 설정할 경우 스키마를 생성·삭제하므로 폐기 가능한 전용 DB만 사용합니다. */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=${REVIEW_REPORT_TEST_URL:jdbc:h2:mem:review-reports;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${REVIEW_REPORT_TEST_USER:sa}",
        "spring.datasource.password=${REVIEW_REPORT_TEST_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "security.app.frontend-origin=http://localhost:3000",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.access-token-expires-minutes=15", "security.jwt.refresh-token-expires-days=7",
        "storage.endpoint=https://example.com", "storage.region=auto", "storage.bucket=test-bucket",
        "storage.access-key=test-access-key", "storage.secret-key=test-secret-key",
        "storage.public-base-url=https://images.example.com", "storage.image-cleanup.enabled=false",
        "member.withdrawal-cleanup.enabled=false"
})
@AutoConfigureMockMvc
class ReviewReportTests {
    private static final Instant NOW = Instant.parse("2026-11-19T14:59:59Z");
    private static final String ADMIN_PATH = "/api/v1/admin/reviews/reports";
    private static final String TYPE_PATH = "/api/v1/review-report-types";
    private static final String ADMIN_TYPE_PATH = "/api/v1/admin/review-report-types";

    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;
    @Autowired ReviewReportService service;
    @Autowired ReviewReportTypeService typeService;
    @Autowired MemberDeletionService memberDeletion;
    @Autowired ReviewRepository reviews;
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired TransactionTemplate transactions;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean ReviewReportRepository reports;
    @MockitoSpyBean ReviewReportTypeRepository types;
    @MockitoSpyBean ReviewImageDeleteRepository imageDeletions;
    @MockitoSpyBean Clock clock;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private Member owner;
    private Member reporter;
    private Member admin;
    private Long reviewId;
    private Long otherReviewId;
    private ReviewReportType reportType;

    @BeforeEach
    void setUp() {
        reset(reports, types, imageDeletions, clock);
        doReturn(NOW).when(clock).instant();
        reports.deleteAll();
        types.deleteAll();
        reviews.deleteAll();
        imageDeletions.deleteAll();
        members.deleteAll();
        categories.deleteAll();
        owner = members.saveAndFlush(new Member("owner@test.com", "owner", "unused"));
        reporter = members.saveAndFlush(new Member("reporter@test.com", "reporter", "unused"));
        admin = new Member("admin@test.com", "admin", "unused");
        ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
        admin = members.saveAndFlush(admin);
        Category category = categories.saveAndFlush(new Category("test"));
        reviewId = saveReview("first review", category);
        otherReviewId = saveReview("second review", category);
        reportType = types.saveAndFlush(new ReviewReportType("광고·도배", 0));
    }

    @Test
    void createsNormalizedReportWithoutReturningPrivateData() throws Exception {
        mvc.perform(report(reviewId, reporter, "  신고 사유  ")).andExpect(status().isCreated())
                .andExpect(content().string(""));
        ReviewReport saved = reports.findAll().getFirst();
        assertThat(saved.getReason()).isEqualTo("신고 사유");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getReportDate()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(saved.getReporter().getId()).isEqualTo(reporter.getId());
        assertThat(saved.getReportType().getId()).isEqualTo(reportType.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":null}", "{\"reason\":\"\"}", "{\"reason\":\"  \\n \\t\"}"})
    void rejectsMissingAndBlankReasons(String body) throws Exception {
        ObjectNode payload = (ObjectNode) mapper.readTree(body);
        payload.put("reportTypeId", reportType.getId());
        mvc.perform(post(path(reviewId)).header("Authorization", bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        assertThat(reports.count()).isZero();
    }

    @Test
    void validatesLengthAfterStripping() throws Exception {
        mvc.perform(report(reviewId, reporter, " " + "가".repeat(1000) + " ")).andExpect(status().isCreated());
        mvc.perform(report(otherReviewId, reporter, "가".repeat(1001))).andExpect(status().isBadRequest());
        assertThat(reports.count()).isEqualTo(1);
    }

    @Test
    void authenticationOwnershipAndWithdrawalPolicies() throws Exception {
        mvc.perform(post(path(reviewId)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"reason\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path(reviewId)).header("Authorization", "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"reason\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(report(reviewId, owner, "reason")).andExpect(status().isForbidden());
        mvc.perform(report(Long.MAX_VALUE, reporter, "reason")).andExpect(status().isNotFound());
        scheduleWithdrawal(reporter, NOW.plusSeconds(86400));
        mvc.perform(report(reviewId, reporter, "reason")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorCode.WITHDRAWAL_CONTENT_WRITE_NOT_ALLOWED.getMessage()));
        mvc.perform(report(reviewId, admin, "admin report")).andExpect(status().isCreated());
        mvc.perform(report(reviewId, admin, "again")).andExpect(status().isConflict());
        assertThat(reports.count()).isEqualTo(1);
    }

    @Test
    void restrictsSameMemberAndReviewPerKoreanDate() throws Exception {
        mvc.perform(report(reviewId, reporter, "first")).andExpect(status().isCreated());
        mvc.perform(report(reviewId, reporter, "duplicate")).andExpect(status().isConflict());
        mvc.perform(report(otherReviewId, reporter, "different review")).andExpect(status().isCreated());
        mvc.perform(report(reviewId, admin, "different member")).andExpect(status().isCreated());
        doReturn(NOW.plusSeconds(1)).when(clock).instant();
        mvc.perform(report(reviewId, reporter, "next Korean date")).andExpect(status().isCreated());
        assertThat(reports.findAll()).extracting(ReviewReport::getReportDate)
                .containsOnly(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 20));
        assertThat(reports.count()).isEqualTo(4);
    }

    @Test
    void databaseUniqueConstraintAlsoReturnsConflict() throws Exception {
        mvc.perform(report(reviewId, reporter, "first")).andExpect(status().isCreated());
        doReturn(false).when(reports).existsByReviewIdAndReporterIdAndReportDate(anyLong(), anyLong(), any());
        mvc.perform(report(reviewId, reporter, "forced collision")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.REVIEW_REPORT_ALREADY_EXISTS.getMessage()));
        assertThat(reports.count()).isEqualTo(1);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "REVIEW_REPORT_TEST_URL", matches = "jdbc:postgresql:.*")
    void postgresMigrationCanBeReappliedAndSupportsTheApi() throws Exception {
        // 이 클래스 전용 폐기 가능 DB에서만 신고 테이블을 SQL로 다시 만듭니다.
        jdbc.execute("drop table review_reports");
        ResourceDatabasePopulator migration = new ResourceDatabasePopulator(
                new FileSystemResource("database/review-reports.sql"));
        migration.setSqlScriptEncoding("UTF-8");
        migration.execute(dataSource);
        migration.execute(dataSource);
        jdbc.update("""
                insert into review_reports (review_id, reporter_id, reason, report_date, created_at)
                values (?, ?, ?, ?, ?)
                """, otherReviewId, reporter.getId(), "legacy content", LocalDate.of(2026, 11, 19),
                java.sql.Timestamp.from(NOW));
        ResourceDatabasePopulator typeMigration = new ResourceDatabasePopulator(
                new FileSystemResource("database/review-report-types.sql"));
        typeMigration.setSqlScriptEncoding("UTF-8");
        typeMigration.execute(dataSource);
        typeMigration.execute(dataSource);
        var confirmationMigration = new ResourceDatabasePopulator(
                new FileSystemResource("database/review-report-confirmation.sql"));
        confirmationMigration.execute(dataSource);
        confirmationMigration.execute(dataSource);
        assertThat(jdbc.queryForObject(
                "select confirmed from review_reports where reason = 'legacy content'", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("""
                select t.name from review_reports p join review_report_types t on t.id = p.report_type_id
                where p.reason = 'legacy content'
                """, String.class)).isEqualTo("기존 신고(사유 미분류)");
        assertThat(types.findAll().stream().filter(type -> !type.isActive()).map(ReviewReportType::getName))
                .containsExactly("기존 신고(사유 미분류)");
        assertThat(jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                where table_schema = current_schema() and table_name = 'review_reports' and column_name = 'report_type_id'
                """, String.class)).isEqualTo("NO");
        mvc.perform(get(TYPE_PATH).header("Authorization", bearer(reporter)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(report(reviewId, reporter, "SQL-created table")).andExpect(status().isCreated());
        doReturn(false).when(reports).existsByReviewIdAndReporterIdAndReportDate(anyLong(), anyLong(), any());
        mvc.perform(report(reviewId, reporter, "duplicate")).andExpect(status().isConflict());
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].reason").value("SQL-created table"));
        assertThat(reviews.count()).isEqualTo(2);
        assertThat(members.count()).isEqualTo(3);
    }

    @Test
    void unrelatedDatabaseErrorIsNotReportedAsDuplicate() throws Exception {
        doThrow(new DataIntegrityViolationException("unrelated constraint")).when(reports).saveAndFlush(any());
        mvc.perform(report(reviewId, reporter, "reason")).andExpect(status().isInternalServerError());
        assertThat(reports.count()).isZero();
    }

    @Test
    void concurrentReportsCreateExactlyOneRow() throws Exception {
        String authorization = bearer(reporter);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(post(path(reviewId)).header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(new ReviewReportRequest(reportType.getId(), "concurrent"))))
                        .andReturn().getResponse().getStatus();
            })).toList();
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(reports.count()).isEqualTo(1);
    }

    @Test
    void onlyAdminCanReadReportsWithPaginationAndFiltering() throws Exception {
        mvc.perform(report(reviewId, reporter, "first")).andExpect(status().isCreated());
        mvc.perform(report(otherReviewId, reporter, "second")).andExpect(status().isCreated());
        mvc.perform(report(reviewId, admin, "third")).andExpect(status().isCreated());
        mvc.perform(get(ADMIN_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(reporter))).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].reason").value("third"))
                .andExpect(jsonPath("$.content[0].reviewId").value(reviewId))
                .andExpect(jsonPath("$.content[0].reviewTitle").value("first review"))
                .andExpect(jsonPath("$.content[0].author.nickname").value("owner"))
                .andExpect(jsonPath("$.content[0].reporter.nickname").value("admin"));
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin)).param("reviewId", reviewId.toString())
                        .param("size", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content[0].reason").value("first"));
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin)).param("reviewId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin)).param("page", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,0", "size,101", "confirmed,invalid"})
    void validatesAdminPageParameters(String name, String value) throws Exception {
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin)).param(name, value))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersConfirmedReportsAndSupportsReopening() throws Exception {
        mvc.perform(report(reviewId, reporter, "target")).andExpect(status().isCreated());
        mvc.perform(report(reviewId, admin, "same review")).andExpect(status().isCreated());
        mvc.perform(report(otherReviewId, reporter, "different review")).andExpect(status().isCreated());
        Long id = reports.findAll().stream().filter(item -> item.getReason().equals("target"))
                .findFirst().orElseThrow().getId();
        String path = ADMIN_PATH + "/" + id;
        String confirm = "{\"confirmed\":true}";

        mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(confirm))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(path).header("Authorization", bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON).content(confirm))
                .andExpect(status().isForbidden());
        for (String invalid : List.of("{}", "{\"confirmed\":null}", "{\"confirmed\":\"true\"}")) {
            mvc.perform(patch(path).header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch(ADMIN_PATH + "/" + Long.MAX_VALUE).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(confirm))
                .andExpect(status().isNotFound());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(patch(path).header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(confirm))
                    .andExpect(status().isNoContent());
        }

        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin))
                        .param("reviewId", reviewId.toString()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reason").value("same review"))
                .andExpect(jsonPath("$.content[0].confirmed").value(false));
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin))
                        .param("reviewId", reviewId.toString()).param("confirmed", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].confirmed").value(true));

        mvc.perform(patch(path).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin)).param("confirmed", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @WithMockUser(roles = "USER")
    void serviceAlsoProtectsAdminRead() {
        assertThatThrownBy(() -> service.findAll(1, 50, null, false)).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"author", "admin", "withdrawal"})
    void allReviewDeletionPathsRemoveOnlyRelatedReports(String mode) throws Exception {
        mvc.perform(report(reviewId, reporter, "target")).andExpect(status().isCreated());
        if (!mode.equals("withdrawal")) {
            mvc.perform(report(otherReviewId, reporter, "keep")).andExpect(status().isCreated());
        }
        if (mode.equals("withdrawal")) {
            scheduleWithdrawal(owner, NOW);
            assertThat(memberDeletion.deleteIfDue(owner.getId(), NOW)).isTrue();
        } else {
            String prefix = mode.equals("admin") ? "/api/v1/admin/reviews/" : "/api/v1/reviews/";
            mvc.perform(delete(prefix + reviewId).header("Authorization", bearer(mode.equals("admin") ? admin : owner)))
                    .andExpect(status().isNoContent());
        }
        assertThat(reviews.existsById(reviewId)).isFalse();
        assertThat(reports.count()).isEqualTo(mode.equals("withdrawal") ? 0 : 1);
    }

    @Test
    void reporterWithdrawalKeepsReasonAndDateButRemovesIdentity() throws Exception {
        mvc.perform(report(reviewId, reporter, "keep reason")).andExpect(status().isCreated());
        scheduleWithdrawal(reporter, NOW);
        assertThat(memberDeletion.deleteIfDue(reporter.getId(), NOW)).isTrue();
        assertThat(reports.count()).isEqualTo(1);
        assertThat(reports.findAll().getFirst().getReporter()).isNull();
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].reporter").isEmpty())
                .andExpect(jsonPath("$.content[0].reason").value("keep reason"))
                .andExpect(jsonPath("$.content[0].createdAt").value(NOW.toString()));
        assertThat(reviews.existsById(reviewId)).isTrue();
    }

    @Test
    void reviewDeletionFailureRollsBackReportRemoval() throws Exception {
        mvc.perform(report(reviewId, reporter, "keep")).andExpect(status().isCreated());
        doThrow(new DataIntegrityViolationException("scheduling failed")).when(imageDeletions).saveAll(any());
        mvc.perform(delete("/api/v1/admin/reviews/" + reviewId).header("Authorization", bearer(admin)))
                .andExpect(status().isInternalServerError());
        assertThat(reviews.existsById(reviewId)).isTrue();
        assertThat(reports.count()).isEqualTo(1);
    }

    @Test
    void memberDeletionFailureRollsBackAnonymization() throws Exception {
        mvc.perform(report(reviewId, reporter, "keep identity")).andExpect(status().isCreated());
        scheduleWithdrawal(reporter, NOW);
        doThrow(new DataIntegrityViolationException("scheduling failed")).when(imageDeletions).saveAll(any());
        assertThatThrownBy(() -> memberDeletion.deleteIfDue(reporter.getId(), NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(members.existsById(reporter.getId())).isTrue();
        assertThat(reports.findAll().getFirst().getReporter().getId()).isEqualTo(reporter.getId());
    }

    @Test
    void noReporterReadEditOrDeleteEndpoints() throws Exception {
        mvc.perform(report(reviewId, reporter, "keep")).andExpect(status().isCreated());
        Long id = reports.findAll().getFirst().getId();
        mvc.perform(get(path(reviewId)).header("Authorization", bearer(reporter)))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put(path(reviewId)).header("Authorization", bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"changed\"}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(path(reviewId)).header("Authorization", bearer(reporter)))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get(path(reviewId) + "/" + id).header("Authorization", bearer(reporter)))
                .andExpect(status().isNotFound());
        assertThat(reports.findById(id).orElseThrow().getReason()).isEqualTo("keep");
    }

    @Test
    void activeOptionsAreOrderedAndRequireLogin() throws Exception {
        ReviewReportType later = types.saveAndFlush(new ReviewReportType("욕설·비방", 5));
        ReviewReportType tied = types.saveAndFlush(new ReviewReportType("개인정보 노출", 0));
        ReviewReportType inactive = new ReviewReportType("비활성 항목", 0);
        inactive.update(inactive.getName(), 0, false);
        types.saveAndFlush(inactive);
        mvc.perform(get(TYPE_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(TYPE_PATH).header("Authorization", bearer(reporter)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(reportType.getId()))
                .andExpect(jsonPath("$[1].id").value(tied.getId()))
                .andExpect(jsonPath("$[2].id").value(later.getId()))
                .andExpect(jsonPath("$[0].active").doesNotExist());
        mvc.perform(get(ADMIN_TYPE_PATH).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(4));
    }

    @ParameterizedTest
    @CsvSource({"false,401", "true,403"})
    void typeManagementRequiresAdmin(boolean loggedIn, int expectedStatus) throws Exception {
        List<MockHttpServletRequestBuilder> requests = List.of(
                get(ADMIN_TYPE_PATH),
                put(ADMIN_TYPE_PATH + "/order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportTypeIds\":[]}"),
                post(ADMIN_TYPE_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"new\",\"displayOrder\":0}"),
                put(ADMIN_TYPE_PATH + "/" + reportType.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"updated\",\"displayOrder\":1,\"active\":false}"));
        for (MockHttpServletRequestBuilder request : requests) {
            if (loggedIn) request.header("Authorization", bearer(reporter));
            mvc.perform(request).andExpect(status().is(expectedStatus));
        }
        assertThat(types.findAll()).extracting(ReviewReportType::getName).containsExactly("광고·도배");
    }

    @Test
    void adminCanCreateRenameReorderDeactivateAndReactivate() throws Exception {
        mvc.perform(post(ADMIN_TYPE_PATH).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  욕설·비방  \",\"displayOrder\":5}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("욕설·비방"))
                .andExpect(jsonPath("$.displayOrder").value(5)).andExpect(jsonPath("$.active").value(true));
        mvc.perform(report(reviewId, reporter, "keep selected type")).andExpect(status().isCreated());
        mvc.perform(updateType(reportType.getId(), "변경된 사유", 10, false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get(TYPE_PATH).header("Authorization", bearer(reporter)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("욕설·비방"));
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].reportType.id").value(reportType.getId()))
                .andExpect(jsonPath("$.content[0].reportType.name").value("변경된 사유"))
                .andExpect(jsonPath("$.content[0].reason").value("keep selected type"));
        mvc.perform(report(otherReviewId, reporter, "inactive")).andExpect(status().isBadRequest());
        mvc.perform(updateType(reportType.getId(), "변경된 사유", 0, true)).andExpect(status().isOk());
        mvc.perform(get(TYPE_PATH).header("Authorization", bearer(reporter)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(reportType.getId()));
        mvc.perform(report(otherReviewId, reporter, "active again")).andExpect(status().isCreated());
        mvc.perform(delete(ADMIN_TYPE_PATH + "/" + reportType.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isMethodNotAllowed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\" \",\"displayOrder\":0}",
            "{\"name\":\"new\"}", "{\"name\":\"new\",\"displayOrder\":-1}"})
    void validatesTypeCreation(String body) throws Exception {
        mvc.perform(post(ADMIN_TYPE_PATH).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(types.count()).isEqualTo(1);
    }


    @Test
    void reordersAllTypesAndNameUpdatesPreserveTheirOrder() throws Exception {
        ReviewReportType inactive = new ReviewReportType("inactive", 5);
        inactive.update("inactive", 5, false);
        inactive = types.saveAndFlush(inactive);
        ReviewReportType active = types.saveAndFlush(new ReviewReportType("active", 10));

        mvc.perform(put(ADMIN_TYPE_PATH + "/order").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reportTypeIds",
                                List.of(inactive.getId(), active.getId(), reportType.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(inactive.getId()))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[2].displayOrder").value(3));

        mvc.perform(put(ADMIN_TYPE_PATH + "/" + active.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"renamed\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayOrder").value(2));
        mvc.perform(get(TYPE_PATH).header("Authorization", bearer(reporter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("renamed"))
                .andExpect(jsonPath("$[1].id").value(reportType.getId()));
    }

    @Test
    void invalidAndStaleOrdersLeaveExistingOrderIntact() throws Exception {
        ReviewReportType another = types.saveAndFlush(new ReviewReportType("another", 8));
        mvc.perform(put(ADMIN_TYPE_PATH + "/order").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reportTypeIds",
                                List.of(reportType.getId(), reportType.getId())))))
                .andExpect(status().isBadRequest());
        mvc.perform(put(ADMIN_TYPE_PATH + "/order").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reportTypeIds", List.of(another.getId())))))
                .andExpect(status().isConflict());
        assertThat(types.findAllByOrderByDisplayOrderAscIdAsc()).extracting(ReviewReportType::getId)
                .containsExactly(reportType.getId(), another.getId());
    }

    @Test
    void validatesTypeUpdateAndNameLength() throws Exception {
        mvc.perform(put(ADMIN_TYPE_PATH + "/" + reportType.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"new\",\"displayOrder\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(ADMIN_TYPE_PATH).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "가".repeat(101), "displayOrder", 0))))
                .andExpect(status().isBadRequest());
        mvc.perform(updateType(reportType.getId(), "new", -1, true)).andExpect(status().isBadRequest());
        mvc.perform(updateType(Long.MAX_VALUE, "new", 0, true)).andExpect(status().isNotFound());
    }

    @Test
    void typeNamesAreUniqueIncludingConcurrentDatabaseConstraintFallback() throws Exception {
        types.saveAndFlush(new ReviewReportType("other", 1));
        mvc.perform(updateType(reportType.getId(), "other", 0, true)).andExpect(status().isConflict());
        mvc.perform(post(ADMIN_TYPE_PATH).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  광고·도배  \",\"displayOrder\":0}"))
                .andExpect(status().isConflict());
        doReturn(false).when(types).existsByName(anyString());
        mvc.perform(post(ADMIN_TYPE_PATH).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"광고·도배\",\"displayOrder\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.REVIEW_REPORT_TYPE_ALREADY_EXISTS.getMessage()));
        doReturn(false).when(types).existsByNameAndIdNot(anyString(), anyLong());
        mvc.perform(updateType(reportType.getId(), "other", 0, true)).andExpect(status().isConflict());
        assertThat(types.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reportTypeId\":null}", "{\"reportTypeId\":0}", "{\"reportTypeId\":-1}"})
    void selectedTypeIsRequiredAndMustBePositive(String body) throws Exception {
        ObjectNode payload = (ObjectNode) mapper.readTree(body);
        payload.put("reason", "valid content");
        mvc.perform(post(path(reviewId)).header("Authorization", bearer(reporter)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        assertThat(reports.count()).isZero();
    }

    @Test
    void missingTypeFailsAndDifferentTypeDoesNotBypassDailyLimit() throws Exception {
        mvc.perform(post(path(reviewId)).header("Authorization", bearer(reporter)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ReviewReportRequest(Long.MAX_VALUE, "valid content"))))
                .andExpect(status().isNotFound());
        mvc.perform(report(reviewId, reporter, "first")).andExpect(status().isCreated());
        ReviewReportType different = types.saveAndFlush(new ReviewReportType("other", 1));
        mvc.perform(post(path(reviewId)).header("Authorization", bearer(reporter)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ReviewReportRequest(different.getId(), "second"))))
                .andExpect(status().isConflict());
        assertThat(reports.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "USER")
    void typeServiceAlsoRequiresAdmin() {
        assertThatThrownBy(() -> typeService.findAll()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> typeService.create(new ReviewReportTypeCreateRequest("new", 0)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> typeService.update(reportType.getId(), new ReviewReportTypeUpdateRequest("new", 0, false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @EnabledIfEnvironmentVariable(named = "REVIEW_REPORT_TEST_URL", matches = "jdbc:postgresql:.*")
    void postgresSerializesReportCreationAndDeactivation(boolean reportFirst) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                if (reportFirst) {
                    service.create(reviewId, new ReviewReportRequest(reportType.getId(), "first"), reporter.getId());
                } else {
                    ReviewReportType type = types.findByIdForUpdate(reportType.getId()).orElseThrow();
                    type.update(type.getName(), type.getDisplayOrder(), false);
                    types.flush();
                }
                locked.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("lock release timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var waiting = executor.submit(() -> mvc.perform(reportFirst
                                ? updateType(reportType.getId(), reportType.getName(), 0, false)
                                : report(reviewId, reporter, "after deactivation"))
                        .andReturn().getResponse().getStatus());
                awaitPostgresTypeLock();
                assertThat(waiting.isDone()).isFalse();
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                assertThat(waiting.get(10, TimeUnit.SECONDS)).isEqualTo(reportFirst ? 200 : 400);
            } finally {
                release.countDown();
            }
        }
        assertThat(reports.count()).isEqualTo(reportFirst ? 1 : 0);
        assertThat(types.findById(reportType.getId()).orElseThrow().isActive()).isFalse();
    }

    private void awaitPostgresTypeLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and wait_event_type = 'Lock'
                      and query like '%review_report_types%'
                    """, Integer.class);
            if (blocked != null && blocked > 0) return;
            Thread.sleep(20);
        }
        throw new AssertionError("PostgreSQL에서 신고 사유 행 잠금 대기를 확인하지 못했습니다.");
    }

    private MockHttpServletRequestBuilder updateType(Long id, String name, int order, boolean active) {
        return put(ADMIN_TYPE_PATH + "/" + id).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ReviewReportTypeUpdateRequest(name, order, active)));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "REVIEW_REPORT_TEST_URL", matches = "jdbc:postgresql:.*")
    void detailReportsOnlyTheCurrentMembersHistoryForThatReview() throws Exception {
        detail(reviewId, reporter).andExpect(jsonPath("$.isReported").value(false))
                .andExpect(jsonPath("$.isReportedToday").value(false)).andExpect(jsonPath("$.viewCount").value(1));
        mvc.perform(report(reviewId, reporter, "private report content")).andExpect(status().isCreated());
        mvc.perform(updateType(reportType.getId(), reportType.getName(), 0, false)).andExpect(status().isOk());

        detail(reviewId, reporter).andExpect(jsonPath("$.isReported").value(true))
                .andExpect(jsonPath("$.isReportedToday").value(true)).andExpect(jsonPath("$.viewCount").value(2))
                .andExpect(jsonPath("$.reason").doesNotExist()).andExpect(jsonPath("$.reportType").doesNotExist())
                .andExpect(jsonPath("$.reporter").doesNotExist());
        detail(reviewId, admin).andExpect(jsonPath("$.isReported").value(false))
                .andExpect(jsonPath("$.isReportedToday").value(false));
        detail(otherReviewId, reporter).andExpect(jsonPath("$.isReported").value(false))
                .andExpect(jsonPath("$.isReportedToday").value(false));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "REVIEW_REPORT_TEST_URL", matches = "jdbc:postgresql:.*")
    void detailKeepsHistoricalFlagAcrossKoreanMidnightAndAllowsNextDayReport() throws Exception {
        mvc.perform(report(reviewId, reporter, "before midnight")).andExpect(status().isCreated());
        detail(reviewId, reporter).andExpect(jsonPath("$.isReported").value(true))
                .andExpect(jsonPath("$.isReportedToday").value(true));
        doReturn(NOW.plusSeconds(1)).when(clock).instant();
        detail(reviewId, reporter).andExpect(jsonPath("$.isReported").value(true))
                .andExpect(jsonPath("$.isReportedToday").value(false));
        mvc.perform(report(reviewId, reporter, "after midnight")).andExpect(status().isCreated());
        detail(reviewId, reporter).andExpect(jsonPath("$.isReported").value(true))
                .andExpect(jsonPath("$.isReportedToday").value(true));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "REVIEW_REPORT_TEST_URL", matches = "jdbc:postgresql:.*")
    void anonymousDetailDoesNotQueryReportsEvenWhenAReportExists() throws Exception {
        mvc.perform(report(reviewId, reporter, "private")).andExpect(status().isCreated());
        clearInvocations(reports);
        detail(reviewId, null).andExpect(jsonPath("$.isReported").value(false))
                .andExpect(jsonPath("$.isReportedToday").value(false));
        verifyNoInteractions(reports);
    }

    private ResultActions detail(Long id, Member viewer) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/reviews/" + id);
        if (viewer != null) request.header("Authorization", bearer(viewer));
        return mvc.perform(request).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }

    private Long saveReview(String title, Category category) {
        Review review = BeanUtils.instantiateClass(Review.class);
        review.updateDetails(title, mapper.readTree("{\"type\":\"doc\",\"content\":[]}"),
                BigDecimal.ONE, "product", category, null, List.of(), List.of(), null);
        ReflectionTestUtils.setField(review, "author", owner);
        return reviews.saveAndFlush(review).getId();
    }

    private void scheduleWithdrawal(Member member, Instant scheduledAt) {
        transactions.executeWithoutResult(status -> members.findById(member.getId()).orElseThrow()
                .requestWithdrawal(NOW, scheduledAt));
    }

    private String path(Long id) {
        return "/api/v1/reviews/" + id + "/reports";
    }

    private String bearer(Member member) {
        return "Bearer " + tokens.createAccessToken(member);
    }

    private MockHttpServletRequestBuilder report(Long id, Member member, String reason) {
        return post(path(id)).header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ReviewReportRequest(reportType.getId(), reason)));
    }
}

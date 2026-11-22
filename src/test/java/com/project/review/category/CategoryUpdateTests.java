package com.project.review.category;

import com.project.review.category.dtos.CategoryRequest;
import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.category.service.CategoryService;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP 검증과 서비스의 실제 커밋을 함께 확인합니다. 테스트 전체를 트랜잭션으로 감싸지 않습니다. */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:category-update-tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.app.frontend-origin=https://frontend.example.com",
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
class CategoryUpdateTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoSpyBean
    private CategoryRepository categories;
    @Autowired
    private CategoryService service;
    @Autowired
    private MemberRepository members;
    @MockitoSpyBean
    private ReviewRepository reviews;

    private Long categoryId;

    @BeforeEach
    void setUp() {
        reviews.deleteAll();
        categories.deleteAll();
        categoryId = categories.saveAndFlush(new Category("기존 이름")).getId();
    }

    @Test
    void commitsNameAndReturnsUpdatedResponse() throws Exception {
        update(categoryId, "변경된 이름", 200);

        assertThat(storedCategory().getCategory()).isEqualTo("변경된 이름");
    }

    @Test
    void sameNameDoesNotChangeUpdatedAt() throws Exception {
        Category before = storedCategory();

        update(categoryId, "기존 이름", 200);

        Category after = storedCategory();
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    }

    @Test
    void missingCategoryReturns404() throws Exception {
        update(Long.MAX_VALUE, "새 이름", 404);
    }

    @Test
    void duplicateNameReturns409WithoutChangingStoredName() throws Exception {
        categories.saveAndFlush(new Category("사용 중인 이름"));

        update(categoryId, "사용 중인 이름", 409);

        assertThat(storedCategory().getCategory()).isEqualTo("기존 이름");
    }

    @Test
    void databaseDuplicateAfterPrecheckReturns409AndRollsBack() throws Exception {
        categories.saveAndFlush(new Category("동시 요청의 이름"));
        // 사전 조회 직후 다른 요청이 같은 이름을 커밋한 상황을 재현합니다.
        doReturn(false).when(categories).existsByCategoryAndIdNot("동시 요청의 이름", categoryId);

        update(categoryId, "동시 요청의 이름", 409);

        assertThat(storedCategory().getCategory()).isEqualTo("기존 이름");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void invalidNamesReturn400(String name) throws Exception {
        update(categoryId, name, 400);
        assertThat(storedCategory().getCategory()).isEqualTo("기존 이름");
    }

    @Test
    void tooLongNameReturns400() throws Exception {
        update(categoryId, "가".repeat(256), 400);
        assertThat(storedCategory().getCategory()).isEqualTo("기존 이름");
    }

    @Test
    void maximumLengthNameIsAccepted() throws Exception {
        update(categoryId, "가".repeat(255), 200);
        assertThat(storedCategory().getCategory()).hasSize(255);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void directServiceCallCannotBypassBlankValidation(String name) {
        assertThatThrownBy(() -> service.update(categoryId, new CategoryRequest(name)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(storedCategory().getCategory()).isEqualTo("기존 이름");
    }

    @Test
    void directServiceCallCannotBypassLengthValidation() {
        assertThatThrownBy(() -> service.update(categoryId, new CategoryRequest("가".repeat(256))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(storedCategory().getCategory()).isEqualTo("기존 이름");
    }

    @Test
    void missingReviewReturns404WithoutCategoryVersionColumn() throws Exception {
        mvc.perform(get("/api/v1/reviews/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void regularUserCannotUpdateCategory() throws Exception {
        mvc.perform(patch("/api/v1/admin/categories/{id}", categoryId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("새 이름"))))
                .andExpect(status().isForbidden());
        assertThat(storedCategory().getCategory()).isEqualTo("기존 이름");
    }

    @Test
    void anonymousCanListCategories() throws Exception {
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("기존 이름"));
    }

    @Test
    void adminCanCreateCategoryAndDuplicateNameReturns409() throws Exception {
        String request = objectMapper.writeValueAsString(new CategoryRequest("새 카테고리"));
        mvc.perform(post("/api/v1/admin/categories")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("새 카테고리"));
        mvc.perform(post("/api/v1/admin/categories")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());
        assertThat(categories.count()).isEqualTo(2);
    }

    @Test
    void anonymousCannotCreateCategory() throws Exception {
        mvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("새 이름"))))
                .andExpect(status().isUnauthorized());
        assertThat(categories.count()).isEqualTo(1);
    }

    @Test
    void regularUserCannotCreateOrDeleteCategory() throws Exception {
        mvc.perform(post("/api/v1/admin/categories")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("새 이름"))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/categories/{id}", categoryId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        assertThat(categories.existsById(categoryId)).isTrue();
    }

    @Test
    void adminCanDeleteUnusedCategory() throws Exception {
        mvc.perform(delete("/api/v1/admin/categories/{id}", categoryId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
        assertThat(categories.existsById(categoryId)).isFalse();
    }

    @Test
    void oldWriteRoutesAreRemoved() throws Exception {
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(post("/api/v1/categories").with(admin)).andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/api/v1/categories/{id}", categoryId).with(admin)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/categories/{id}", categoryId).with(admin)).andExpect(status().isNotFound());
        assertThat(categories.existsById(categoryId)).isTrue();
    }

    @Test
    void adminOrderRoutesRejectAnonymousAndRegularUsers() throws Exception {
        mvc.perform(get("/api/v1/admin/categories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/categories").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/categories/order")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/admin/categories/order").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void adminCategoryPatchPreflightAllowsConfiguredFrontend() throws Exception {
        mvc.perform(options("/api/v1/admin/categories/{id}", categoryId)
                        .header("Origin", "https://frontend.example.com")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.com"));
    }

    @Test
    void savedOrderIsSharedByListsAndMemberCountsAndNewCategoriesAppend() throws Exception {
        Long secondId = categories.save(new Category("두 번째")).getId();
        Long thirdId = categories.save(new Category("세 번째")).getId();
        order("""
                {"categoryIds":[%d,%d,%d]}
                """.formatted(thirdId, categoryId, secondId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(thirdId));

        service.create(new CategoryRequest("신규"));
        for (String path : new String[]{"/api/v1/categories", "/api/v1/admin/categories"}) {
            mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(thirdId))
                    .andExpect(jsonPath("$[1].id").value(categoryId))
                    .andExpect(jsonPath("$[2].id").value(secondId))
                    .andExpect(jsonPath("$[3].category").value("신규"));
        }
        mvc.perform(get("/api/v1/member/count").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(thirdId))
                .andExpect(jsonPath("$[1].categoryId").value(categoryId))
                .andExpect(jsonPath("$[2].categoryId").value(secondId))
                .andExpect(jsonPath("$[3].category").value("신규"))
                .andExpect(jsonPath("$[3].count").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"categoryIds\":null}", "{\"categoryIds\":[null]}",
            "{\"categoryIds\":[0]}", "{\"categoryIds\":[-1]}", "{\"categoryIds\":[1,1]}"})
    void invalidOrderReturns400(String request) throws Exception {
        order(request).andExpect(status().isBadRequest());
        assertThat(storedCategory().getDisplayOrder()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void staleOrderReturns409WithoutPersistingPartialOrder() throws Exception {
        order("""
                {"categoryIds":[%d,%d]}
                """.formatted(categoryId, Long.MAX_VALUE)).andExpect(status().isConflict());
        order("{\"categoryIds\":[]}").andExpect(status().isConflict());
        assertThat(storedCategory().getDisplayOrder()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void emptyOrderIsAcceptedWhenThereAreNoCategories() throws Exception {
        categories.deleteById(categoryId);
        order("{\"categoryIds\":[]}").andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void referencedCategoryCannotBeDeletedEvenIfPrecheckMissesTheReview() throws Exception {
        Member author = members.save(Member.create("category-author@example.com", "category-author", "unused"));
        reviews.saveAndFlush(Review.create(new ReviewRequest("review", "product",
                objectMapper.readTree("{\"type\":\"doc\",\"content\":[]}"), BigDecimal.ONE,
                null, null, null, null, null, null, null), author, storedCategory()));
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

        mvc.perform(delete("/api/v1/admin/categories/{id}", categoryId).with(admin))
                .andExpect(status().isConflict());
        doReturn(false).when(reviews).existsByCategoryId(categoryId);
        mvc.perform(delete("/api/v1/admin/categories/{id}", categoryId).with(admin))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/admin/categories/{id}", Long.MAX_VALUE).with(admin))
                .andExpect(status().isNotFound());
        assertThat(categories.existsById(categoryId)).isTrue();
        assertThat(reviews.count()).isEqualTo(1);
    }

    private ResultActions order(String request) throws Exception {
        return mvc.perform(put("/api/v1/admin/categories/order")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content(request));
    }

    private void update(Long id, String name, int expectedStatus) throws Exception {
        ResultActions result = mvc.perform(patch("/api/v1/admin/categories/{id}", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest(name))))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 200) {
            result.andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.category").value(name));
        }
    }

    private Category storedCategory() {
        return categories.findById(categoryId).orElseThrow();
    }
}

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:filter-regression;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
    "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "security.jwt.access-token-expires-minutes=15", "security.jwt.refresh-token-expires-days=7",
    "storage.endpoint=https://example.com", "storage.region=auto", "storage.bucket=test-bucket",
    "storage.access-key=test-access-key", "storage.secret-key=test-secret-key",
    "storage.public-base-url=https://images.example.com"
})
@AutoConfigureMockMvc
@Transactional
class ReviewCategoryFilterTests {
    @Autowired MockMvc mvc;
    @Autowired ReviewRepository reviews;
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired ReviewLikeRepository likes;
    private Member owner;
    private Category selected;
    private Category empty;

    @BeforeEach
    void setUp() {
        owner = members.save(Member.create("filter-owner@example.com", "filter-owner", "unused"));
        Member other = members.save(Member.create("filter-other@example.com", "filter-other", "unused"));
        selected = categories.save(new Category("selected"));
        empty = categories.save(new Category("empty"));
        Category different = categories.save(new Category("different"));
        save(owner, selected); save(owner, different); save(other, selected);
        reviews.flush();
    }

    private void save(Member author, Category category) {
        reviews.save(Review.create(new ReviewRequest("review", "Product", JsonMapper.builder().build().readTree("{\"type\":\"doc\",\"content\":[]}"), BigDecimal.ONE,
                null, null, null, null, null, null, null),
                author, category));
    }

    @Test
    void memberReviewsReflectOwnLikesAndCancellation() throws Exception {
        Review liked = reviews.findAll().stream()
                .filter(review -> review.getAuthor().getId().equals(owner.getId()))
                .filter(review -> review.getCategory().getId().equals(selected.getId()))
                .findFirst().orElseThrow();
        likes.saveAndFlush(new ReviewLike(liked, owner));
        String likedPath = "$.content[?(@.id == " + liked.getId() + ")]";
        String unlikedPath = "$.content[?(@.id != " + liked.getId() + ")]";
        mvc.perform(get("/api/v1/member/reviews").with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath(likedPath + ".isLike").value(contains(true)))
                .andExpect(jsonPath(unlikedPath + ".isLike").value(contains(false)))
                .andExpect(jsonPath(likedPath + ".likeCount").value(contains(1)));
        likes.deleteAll();
        likes.flush();
        mvc.perform(get("/api/v1/member/reviews").with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath(likedPath + ".isLike").value(contains(false)))
                .andExpect(jsonPath(likedPath + ".likeCount").value(contains(0)));
    }
    @Test
    void publicCategoryOnlyQueryReturnsMatchingReviews() throws Exception {
        mvc.perform(get("/api/v1/reviews").param("categoryId", selected.getId().toString()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].category.id").value(selected.getId()));
    }

    @Test
    void emptyCategoryAndCategorySearchNeverFallBackToAllReviews() throws Exception {
        mvc.perform(get("/api/v1/reviews").param("categoryId", empty.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/v1/reviews").param("categoryId", selected.getId().toString()).param("query", "missing"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void memberCategoryFiltersByBothOwnerAndCategory() throws Exception {
        mvc.perform(get("/api/v1/member/reviews").with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .param("categoryId", selected.getId().toString()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category.id").value(selected.getId()))
                .andExpect(jsonPath("$.content[0].author.id").value(owner.getId()));
    }

    @Test
    void emptyMemberCategoryStaysEmpty() throws Exception {
        mvc.perform(get("/api/v1/member/reviews").with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .param("categoryId", empty.getId().toString()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void outOfRangeMemberPageKeepsFilterAndPaginationMetadata() throws Exception {
        mvc.perform(get("/api/v1/member/reviews").with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .param("categoryId", selected.getId().toString()).param("size", "1").param("page", "9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(9)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalPages").value(1)).andExpect(jsonPath("$.content").isEmpty());
    }
}

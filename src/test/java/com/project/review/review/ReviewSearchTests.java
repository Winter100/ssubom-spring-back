package com.project.review.review;

import com.project.review.review.dtos.ReviewRequest;

import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.repository.ReviewViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.project.review.review.ReviewContentFixtures.textDocument;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
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
class ReviewSearchTests {

    @Autowired private MockMvc mvc;
    @Autowired private MemberRepository members;
    @Autowired private CategoryRepository categories;
    @Autowired private ReviewRepository reviews;
    @MockitoBean private ReviewViewRepository views;

    private Category selected;
    private Category other;
    private Long firstMatchId;
    private Long secondMatchId;

    @BeforeEach
    void setUp() {
        Member author = members.save(Member.create("exact@example.com", "writer", "unused"));
        Member similarAuthor = members.save(Member.create("similar@example.com", "writer-more", "unused"));
        selected = categories.save(new Category("selected"));
        other = categories.save(new Category("other"));
        firstMatchId = save("123 first", author, selected);
        secondMatchId = save("middle 123 end", author, selected);
        save("last 123", similarAuthor, selected);
        save("123 other category", author, other);
        save("literal %_ mark", author, selected);
    }

    @Test
    void titleContainsQueryAndCategoryFiltersBeforePaginationAndCount() throws Exception {
        when(views.findCounts(List.of(secondMatchId))).thenReturn(Map.of(secondMatchId, 8L));
        mvc.perform(get("/api/v1/reviews")
                        .param("searchType", "title").param("categoryId", selected.getId().toString())
                        .param("query", "middle").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(secondMatchId))
                .andExpect(jsonPath("$.content[0].viewCount").value(8));
        mvc.perform(get("/api/v1/reviews")
                        .param("searchType", "title").param("categoryId", selected.getId().toString())
                        .param("query", "123").param("size", "2").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(firstMatchId));
        verify(views, never()).increment(anyLong());
    }

    @Test
    void authorRequiresExactNicknameAndCanCombineWithCategory() throws Exception {
        mvc.perform(get("/api/v1/reviews").param("searchType", "author").param("query", "writer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[*].author.nickname", everyItem(is("writer"))));
        mvc.perform(get("/api/v1/reviews").param("searchType", "author").param("query", "writer")
                        .param("categoryId", selected.getId().toString()).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].author.nickname", everyItem(is("writer"))));
        mvc.perform(get("/api/v1/reviews").param("searchType", "author").param("query", "writer")
                        .param("categoryId", selected.getId().toString()).param("size", "2").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[*].author.nickname", everyItem(is("writer"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"writ", "missing"})
    void unmatchedAuthorReturnsEmptyResultsWithOrWithoutCategory(String nickname) throws Exception {
        mvc.perform(get("/api/v1/reviews").param("searchType", "author").param("query", nickname))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        mvc.perform(get("/api/v1/reviews").param("searchType", "author").param("query", nickname)
                        .param("categoryId", selected.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void authorWithoutReviewsReturnsEmptyResults() throws Exception {
        members.save(Member.create("empty@example.com", "empty-writer", "unused"));
        mvc.perform(get("/api/v1/reviews").param("searchType", "author").param("query", "empty-writer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void outOfRangeAuthorPageKeepsFilteredTotalAndEmptyContent() throws Exception {
        mvc.perform(get("/api/v1/reviews").param("searchType", "author").param("query", "writer")
                        .param("size", "2").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "%_"})
    void titleTreatsWildcardCharactersLiterally(String query) throws Exception {
        mvc.perform(get("/api/v1/reviews").param("searchType", "title").param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("literal %_ mark"));
    }

    @Test
    void optionalSearchAndCategoryConditionsWorkIndependently() throws Exception {
        mvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get("/api/v1/reviews").param("categoryId", selected.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/v1/reviews").param("query", "123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/v1/reviews").param("searchType", "author").param("query", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get("/api/v1/reviews").param("query", "missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void invalidSearchTypeReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/reviews").param("searchType", "content").param("query", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "9223372036854775808"})
    void invalidCategoryIdReturnsBadRequest(String categoryId) throws Exception {
        mvc.perform(get("/api/v1/reviews").param("categoryId", categoryId))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "author"})
    void unmatchedSearchKeepsEmptyResultsAcrossPages(String searchType) throws Exception {
        for (int page : List.of(1, 2)) {
            mvc.perform(get("/api/v1/reviews").param("searchType", searchType)
                            .param("query", "missing").param("size", "2").param("page", Integer.toString(page)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    @ParameterizedTest
    @CsvSource({"title,last 123", "author,writer-more"})
    void categorySearchDoesNotReturnMatchesFromOtherCategories(String searchType, String query) throws Exception {
        mvc.perform(get("/api/v1/reviews").param("searchType", searchType).param("query", query)
                        .param("categoryId", other.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void missingCategoryKeepsEmptyFilteredResults() throws Exception {
        mvc.perform(get("/api/v1/reviews").param("categoryId", Long.toString(Long.MAX_VALUE))
                        .param("query", "missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void outOfRangeSearchPageDoesNotFallBackToOtherReviews() throws Exception {
        mvc.perform(get("/api/v1/reviews").param("query", "middle").param("page", "2")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void searchWithNoReviewsReturnsEmptyPage() throws Exception {
        reviews.deleteAll();
        mvc.perform(get("/api/v1/reviews").param("query", "missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    private Long save(String title, Member author, Category category) {
        return reviews.saveAndFlush(Review.create(new ReviewRequest(title, "Product", textDocument("body"), BigDecimal.ONE,
                null, null, null, null, null, null, null),
                author, category)).getId();
    }
}

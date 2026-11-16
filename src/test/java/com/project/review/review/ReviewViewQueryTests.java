package com.project.review.review;

import com.project.review.review.dtos.ReviewRequest;

import com.project.review.category.entities.Category;
import com.project.review.member.entities.Member;
import com.project.review.auth.service.TokenService;
import com.project.review.review.controller.ReviewController;
import com.project.review.review.dtos.ReviewSummaryResponse;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.repository.ReviewViewRepository;
import com.project.review.review.service.ReviewService;
import com.project.review.review.service.ReviewLikeService;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static com.project.review.review.ReviewContentFixtures.textDocument;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReviewViewQueryTests {

    @Mock
    private ReviewRepository reviews;
    @Mock
    private ReviewViewRepository views;
    @Mock
    private ReviewLikeService likes;
    @InjectMocks
    private ReviewService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ReviewController(service, mock(TokenService.class)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void missingReviewDoesNotIncrement() {
        when(reviews.findById(99L)).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.findByIdAndCountView(99L, null))
                .isInstanceOf(com.project.review.common.exception.BusinessException.class);
        verifyNoInteractions(views);
    }

    @Test
    void returnsPageDataWithViewCounts() throws Exception {
        PageRequest pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        ReviewSummaryResponse summary = new ReviewSummaryResponse(
                3L, "목록 리뷰", Instant.parse("2026-10-28T00:00:00Z"), new BigDecimal("4.25"),
                1L, "작성자", null, 2L, "카테고리", "reviews/main.png", false);
        when(reviews.findAllSummaries(pageable)).thenReturn(new PageImpl<>(List.of(summary), pageable, 5));

        when(views.findCounts(List.of(3L))).thenReturn(Map.of(3L, 7L));
        when(likes.findLikeCounts(List.of(3L))).thenReturn(Map.of(3L, 2L));

        mvc.perform(get("/api/v1/reviews").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.content[0].title").value("목록 리뷰"))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-10-28T00:00:00Z"))
                .andExpect(jsonPath("$.content[0].rating").value(4.25))
                .andExpect(jsonPath("$.content[0].author.id").value(1))
                .andExpect(jsonPath("$.content[0].author.nickname").value("작성자"))
                .andExpect(jsonPath("$.content[0].category.id").value(2))
                .andExpect(jsonPath("$.content[0].category.category").value("카테고리"))
                .andExpect(jsonPath("$.content[0].mainImageKey").value("reviews/main.png"))
                .andExpect(jsonPath("$.content[0].viewCount").value(7))
                .andExpect(jsonPath("$.content[0].likeCount").value(2));

        verify(views).findCounts(List.of(3L));
        verifyNoMoreInteractions(views);
    }

    @Test
    void countsEveryDetailRequestWithoutIssuingVisitorCookie() throws Exception {
        Member author = Member.create(null, "작성자", null);
        Category category = new Category("카테고리");
        ReflectionTestUtils.setField(author, "id", 1L);
        ReflectionTestUtils.setField(category, "id", 2L);
        Review review = Review.create(new ReviewRequest("상세 리뷰", null, textDocument("상세 본문"), new BigDecimal("4.25"),
                null, null, null, "한 달", List.of("장점"), List.of("단점"), "추천합니다"),
                author, category);
        ReflectionTestUtils.setField(review, "id", 3L);
        ReflectionTestUtils.setField(review, "createdAt", Instant.parse("2026-10-28T00:00:00Z"));
        review.addImage("reviews/main.png", 0);
        when(reviews.findById(3L)).thenReturn(Optional.of(review));

        when(views.findCount(3L)).thenReturn(1L, 2L);

        mvc.perform(get("/api/v1/reviews/3"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.title").value("상세 리뷰"))
                .andExpect(jsonPath("$.content.content[0].content[0].text").value("상세 본문"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-28T00:00:00Z"))
                .andExpect(jsonPath("$.rating").value(4.25))
                .andExpect(jsonPath("$.author.id").value(1))
                .andExpect(jsonPath("$.author.nickname").value("작성자"))
                .andExpect(jsonPath("$.category.id").value(2))
                .andExpect(jsonPath("$.category.category").value("카테고리"))
                .andExpect(jsonPath("$.duration").value("한 달"))
                .andExpect(jsonPath("$.pros[0]").value("장점"))
                .andExpect(jsonPath("$.cons[0]").value("단점"))
                .andExpect(jsonPath("$.verdict").value("추천합니다"))
                .andExpect(jsonPath("$.mainImageKey").value("reviews/main.png"))
                .andExpect(jsonPath("$.viewCount").value(1));

        mvc.perform(get("/api/v1/reviews/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(2));
        verify(views, times(2)).increment(3L);
        verify(views, times(2)).findCount(3L);
    }
}

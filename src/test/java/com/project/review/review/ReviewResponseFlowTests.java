package com.project.review.review;

import com.project.review.review.dtos.ReviewRequest;

import com.project.review.category.entities.Category;
import com.project.review.common.exception.BusinessException;
import com.project.review.member.entities.Member;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.repository.ReviewViewRepository;
import com.project.review.review.service.ReviewLikeService;
import com.project.review.review.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewResponseFlowTests {
    @Mock ReviewRepository reviews;
    @Mock ReviewViewRepository views;
    @Mock ReviewLikeService likes;
    @InjectMocks ReviewService service;

    private Review review;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        Member author = Member.create(null, "author", null);
        ReflectionTestUtils.setField(author, "id", 7L);
        review = Review.create(new ReviewRequest("review", "product", mapper.readTree("{\"type\":\"doc\"}"), BigDecimal.ONE,
                null, null, null, null, null, null, null),
                author, new Category("category"));
        ReflectionTestUtils.setField(review, "id", 10L);
        review.addImage("reviews/cover", 0);
        when(reviews.findById(10L)).thenReturn(Optional.of(review));
    }

    @Test
    void detailReturnsMemberLikeAndPostIncrementViewCount() {
        when(likes.isLike(10L, 7L)).thenReturn(true);
        when(likes.countLike(10L)).thenReturn(12L);
        when(views.findCount(10L)).thenReturn(101L);

        var response = service.findByIdAndCountView(10L, 7L);

        assertThat(response.isLike()).isTrue();
        assertThat(response.likeCount()).isEqualTo(12L);
        assertThat(response.viewCount()).isEqualTo(101L);
        assertThat(response.mainImageKey()).isEqualTo("reviews/cover");
        var order = inOrder(views);
        order.verify(views).increment(10L);
        order.verify(views).findCount(10L);
    }

    @Test
    void anonymousDetailReturnsFalseLikeStatus() {
        when(likes.countLike(10L)).thenReturn(12L);

        var response = service.findByIdAndCountView(10L, null);

        assertThat(response.isLike()).isFalse();
        assertThat(response.likeCount()).isEqualTo(12L);
        verify(likes).isLike(10L, null);
    }

    @Test
    void editReturnsFormDataWithoutFetchingOrSerializingStatistics() {
        var response = service.findByIdEditView(10L, 7L);
        var json = mapper.valueToTree(response);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.content()).isEqualTo(review.getContent());
        assertThat(response.mainImageKey()).isEqualTo("reviews/cover");
        assertThat(json.has("viewCount")).isFalse();
        assertThat(json.has("isLike")).isFalse();
        assertThat(json.has("likeCount")).isFalse();
        verifyNoInteractions(views, likes);
    }

    @Test
    void editRejectsOtherMembersBeforeFetchingStatistics() {
        assertThatThrownBy(() -> service.findByIdEditView(10L, 8L))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(views, likes);
    }
}

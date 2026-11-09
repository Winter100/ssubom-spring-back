package com.project.review.review;

import com.project.review.member.entities.Member;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewLikeRepository;
import com.project.review.review.service.ReviewLikeService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewLikeServiceTests {

    private final ReviewLikeRepository repository = mock(ReviewLikeRepository.class);
    private final ReviewLikeService service = new ReviewLikeService(repository);

    @Test
    void anonymousAndEmptyPageSkipLikeQueries() {
        assertFalse(service.isLike(30L, null));
        assertEquals(Set.of(), service.findLikedReviewIds(null, List.of(30L)));
        assertEquals(Set.of(), service.findLikedReviewIds(7L, List.of()));
        verifyNoInteractions(repository);
    }

    @Test
    void looksUpOnlyTheRequestedMembersPage() {
        List<Long> reviewIds = List.of(30L, 29L, 28L);
        when(repository.findLikedReviewIds(7L, reviewIds)).thenReturn(Set.of(30L, 28L));

        assertEquals(Set.of(30L, 28L), service.findLikedReviewIds(7L, reviewIds));
        verify(repository).findLikedReviewIds(7L, reviewIds);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void insertAndDeletePassMemberAndReviewIdsInTheCorrectOrder() {
        Review review = mock(Review.class);
        Member member = mock(Member.class);
        when(review.getId()).thenReturn(30L);
        when(member.getId()).thenReturn(7L);

        service.insertLike(review, member);
        service.deleteLike(review, member);

        verify(repository).insertLike(7L, 30L);
        verify(repository).deleteLike(7L, 30L);
        verifyNoMoreInteractions(repository);
    }
}

package com.project.review.review;

import com.project.review.member.entities.Member;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewLikeRepository;
import com.project.review.review.service.ReviewLikeService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewLikeServiceTests {

    private final ReviewLikeRepository repository = mock(ReviewLikeRepository.class);
    private final ReviewLikeService service = new ReviewLikeService(repository);

    @Test
    void emptyPageSkipsLikeCountQuery() {
        assertEquals(Map.of(), service.findLikeCounts(List.of()));
        verifyNoInteractions(repository);
    }

    @Test
    void mapsBatchedLikeCountsAndLeavesZeroCountReviewsAbsent() {
        List<Long> reviewIds = List.of(30L, 29L);
        ReviewLikeRepository.ReviewLikeCount count = mock(ReviewLikeRepository.ReviewLikeCount.class);
        when(count.getReviewId()).thenReturn(30L);
        when(count.getLikeCount()).thenReturn(2L);
        when(repository.findLikeCounts(reviewIds)).thenReturn(List.of(count));

        Map<Long, Long> counts = service.findLikeCounts(reviewIds);

        assertEquals(Map.of(30L, 2L), counts);
        assertEquals(0L, counts.getOrDefault(29L, 0L));
        verify(repository).findLikeCounts(reviewIds);
        verifyNoMoreInteractions(repository);
    }

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

        service.insertLike(review.getId(), member.getId());
        service.deleteLike(review.getId(), member.getId());

        verify(repository).insertLike(7L, 30L);
        verify(repository).deleteLike(7L, 30L);
        verifyNoMoreInteractions(repository);
    }
}

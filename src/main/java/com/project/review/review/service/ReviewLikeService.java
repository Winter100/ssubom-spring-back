package com.project.review.review.service;

import com.project.review.member.entities.Member;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReviewLikeService {

    private final ReviewLikeRepository reviewLikeRepository;

    @Transactional(readOnly = true)
    public boolean isLike(Long reviewId, Long memberId) {
        if (memberId == null) {
            return false;
        }

        return reviewLikeRepository.existsByReviewIdAndMemberId(reviewId, memberId);
    }

    @Transactional(readOnly = true)
    public Set<Long> findLikedReviewIds(Long memberId, List<Long> reviewIds) {
        if (memberId == null || reviewIds.isEmpty()) {
            return Set.of();
        }

        return reviewLikeRepository.findLikedReviewIds(memberId, reviewIds);
    }

    @Transactional
    public void insertLike(Review review, Member member) {
        reviewLikeRepository.insertLike(member.getId(), review.getId());
    }

    @Transactional
    public void deleteLike(Review review, Member member) {
        reviewLikeRepository.deleteLike(member.getId(), review.getId());
    }
}

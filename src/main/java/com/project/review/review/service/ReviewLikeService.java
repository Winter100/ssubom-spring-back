package com.project.review.review.service;

import com.project.review.member.entities.Member;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewLike;
import com.project.review.review.repository.ReviewLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewLikeService {

    private final ReviewLikeRepository reviewLikeRepository;

    @Transactional(readOnly = true)
    public Map<Long, Long> findLikeCounts(List<Long> reviewIds){
        if(reviewIds.isEmpty()){
            return Map.of();
        }

        return reviewLikeRepository.findLikeCounts(reviewIds).stream()
                .collect(Collectors.toMap(
                        ReviewLikeRepository.ReviewLikeCount::getReviewId,
                        ReviewLikeRepository.ReviewLikeCount::getLikeCount
                ));
    }

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
    public Long countLike(Long reviewId) {
        return reviewLikeRepository.countByReviewId(reviewId);
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

package com.project.review.member.service;

import com.project.review.auth.repository.RefreshTokenRepository;
import com.project.review.comment.repository.CommentRepository;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.entities.ReviewImageDelete;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import com.project.review.review.repository.ReviewLikeRepository;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.service.ImageDeletionTime;
import com.project.review.review.service.ReviewService;
import com.project.review.storage.repository.ImageUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemberDeletionService {
    private final MemberRepository members;
    private final ReviewRepository reviews;
    private final ReviewService reviewService;
    private final CommentRepository comments;
    private final ReviewLikeRepository likes;
    private final RefreshTokenRepository tokens;
    private final ImageUploadRepository uploads;
    private final ReviewImageDeleteRepository imageDeletions;
    private final Clock clock;

    // 한 회원의 DB 삭제와 이미지 작업 기록은 모두 커밋되거나 모두 롤백됩니다.
    @Transactional
    public boolean deleteIfDue(Long memberId, Instant cutoff) {
        Member member = members.findByIdForUpdate(memberId).orElse(null);
        if (member == null || member.getWithdrawalScheduledAt() == null
                || member.getWithdrawalScheduledAt().isAfter(cutoff)) {
            return false;
        }

        Set<String> imageKeys = new HashSet<>(uploads.findImageKeysByMemberId(memberId));
        if (member.hasProfileImage()) {
            imageKeys.add(member.getProfileImage());
        }
        long afterId = 0;
        while (true) {
            List<Long> batch = reviews.findIdsByAuthor(memberId, afterId, PageRequest.of(0, 100));
            if (batch.isEmpty()) break;
            for (Long reviewId : batch) {
                imageKeys.addAll(reviewService.removeAndCollectImageKeys(reviewId, memberId));
            }
            afterId = batch.getLast();
        }

        Instant now = clock.instant();
        comments.anonymizeByAuthorId(memberId, now);
        likes.deleteAllByMemberId(memberId);
        tokens.deleteAllByMemberId(memberId);
        uploads.deleteAllByMemberId(memberId);
        Instant deleteImagesAt = ImageDeletionTime.nextMidnight(now);
        imageDeletions.saveAll(imageKeys.stream()
                .map(key -> new ReviewImageDelete(key, deleteImagesAt)).toList());
        members.delete(member);
        // FK 오류도 호출자에게 전달하여 다음 실행에서 회원 전체를 재시도합니다.
        members.flush();
        return true;
    }
}

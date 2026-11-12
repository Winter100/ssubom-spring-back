package com.project.review.comment.service;

import com.project.review.comment.dtos.CommentCreateRequest;
import com.project.review.comment.dtos.CommentPageResponse;
import com.project.review.comment.dtos.CommentResponse;
import com.project.review.comment.dtos.CommentUpdateRequest;
import com.project.review.comment.entities.Comment;
import com.project.review.comment.repository.CommentRepository;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.member.service.MemberService;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {
    private final CommentRepository comments;
    private final ReviewRepository reviews;
    private final MemberService members;

    @Transactional(readOnly = true)
    public CommentPageResponse findAll(Long reviewId, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지는 1 이상, 크기는 1~100이어야 합니다.");
        }
        if (!reviews.existsById(reviewId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        }
        Page<Comment> roots = comments.findByReviewIdAndParentIsNull(reviewId,
                PageRequest.of(page - 1, size, Sort.by("createdAt", "id")));
        List<Long> rootIds = roots.stream().map(Comment::getId).toList();
        List<Comment> replies = rootIds.isEmpty() ? List.of()
                : comments.findByParentIdInOrderByCreatedAtAscIdAsc(rootIds);
        Map<Long, List<CommentResponse>> repliesByParent = replies.stream().collect(Collectors.groupingBy(
                comment -> comment.getParent().getId(),
                Collectors.mapping(comment -> CommentResponse.from(comment, List.of()), Collectors.toList())));
        List<CommentResponse> content = roots.stream()
                .map(comment -> CommentResponse.from(comment, repliesByParent.getOrDefault(comment.getId(), List.of())))
                .toList();
        return new CommentPageResponse(content, page, size, roots.getTotalElements(), roots.getTotalPages());
    }

    public CommentResponse create(Long reviewId, CommentCreateRequest request, Long memberId) {
        Review review = lockReview(reviewId);
        Member author = members.findById(memberId);
        Comment parent = null;
        if (request.parentId() != null) {
            Comment target = findComment(reviewId, request.parentId());
            // 답글 대상이 대댓글이어도 최초 원댓글에 연결해 깊이를 한 단계로 유지합니다.
            parent = target.getParent() == null ? target : target.getParent();
        }
        Comment comment = comments.saveAndFlush(new Comment(review, author, parent, request.content()));
        return CommentResponse.from(comment, List.of());
    }

    public void update(Long reviewId, Long commentId, CommentUpdateRequest request, Long memberId) {
        findEditableComment(reviewId, commentId, memberId).updateContent(request.content());
    }

    public void remove(Long reviewId, Long commentId, Long memberId) {
        findEditableComment(reviewId, commentId, memberId).remove();
    }

    private Review lockReview(Long reviewId) {
        return reviews.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }

    private Comment findEditableComment(Long reviewId, Long commentId, Long memberId) {
        lockReview(reviewId);
        Comment comment = findComment(reviewId, commentId);

        comment.validateAuthor(memberId);

        return comment;
    }

    private Comment findComment(Long reviewId, Long commentId) {
        return comments.findByIdAndReviewId(commentId, reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
    }
}

package com.project.review.comment.entities;

import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.review.entities.Review;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Entity
@Table(name = "comments", indexes = {
        @Index(name = "idx_comments_review", columnList = "review_id"),
        @Index(name = "idx_comments_parent", columnList = "parent_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Member author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private boolean deleted;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public Comment(Review review, Member author, Comment parent, String content) {
        validateContent(content);
        this.review = review;
        this.author = author;
        this.parent = parent;
        this.content = content;
    }

    public void updateContent(String content) {
        validateContent(content);
        this.content = content;
    }

    public void validateAuthor(Long memberId) {
        if (deleted) {
            throw new BusinessException(ErrorCode.COMMENT_DELETED);
        }

        if (!author.getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    public void remove() {
        deleted = true;
        author = null;
        content = "삭제된 댓글입니다";
    }

    private static void validateContent(String content) {
        if (!StringUtils.hasText(content) || content.length() > 1000) {
            throw new IllegalArgumentException("댓글은 공백이 아닌 내용을 포함하여 1~1000자여야 합니다.");
        }
    }
}

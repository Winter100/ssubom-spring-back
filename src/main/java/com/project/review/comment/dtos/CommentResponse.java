package com.project.review.comment.dtos;

import com.project.review.comment.entities.Comment;
import com.project.review.member.entities.Member;

import java.time.Instant;
import java.util.List;

public record CommentResponse(
        Long id, Long parentId, String content, boolean deleted,
        Instant createdAt, Author author, List<CommentResponse> replies
) {
    public record Author(Long id, String nickname, String profileImage) {}

    public static CommentResponse from(Comment comment, List<CommentResponse> replies) {
        Member member = comment.getAuthor();
        Author author = member == null ? null
                : new Author(member.getId(), member.getNickname(), member.getProfileImage());
        return new CommentResponse(comment.getId(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                comment.getContent(), comment.isDeleted(), comment.getCreatedAt(), author, replies);
    }
}

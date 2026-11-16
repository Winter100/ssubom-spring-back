package com.project.review.comment.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** parentId는 답글 대상 댓글 ID이며, 대댓글 ID를 전달해도 최초 원댓글에 연결합니다. */
public record CommentCreateRequest(
        @NotBlank @Size(max = 1000) String content,
        Long parentId
) {}

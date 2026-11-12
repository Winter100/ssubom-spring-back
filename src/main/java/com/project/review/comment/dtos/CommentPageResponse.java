package com.project.review.comment.dtos;

import java.util.List;

public record CommentPageResponse(
        List<CommentResponse> content, int page, int size, long totalElements, int totalPages
) {}

package com.project.review.comment;

import com.project.review.comment.entities.Comment;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentTests {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsEmptyContentOnCreationAndUpdate(String content) {
        assertThatThrownBy(() -> new Comment(null, null, null, content))
                .isInstanceOf(IllegalArgumentException.class);

        Comment comment = new Comment(null, null, null, "original");
        assertThatThrownBy(() -> comment.updateContent(content))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(comment.getContent()).isEqualTo("original");
    }

    @Test
    void enforcesContentLengthOnCreationAndUpdate() {
        String maximumContent = "a".repeat(1000);
        String excessiveContent = "a".repeat(1001);
        Comment comment = new Comment(null, null, null, maximumContent);
        assertThat(comment.getContent()).isEqualTo(maximumContent);
        comment.updateContent("updated");
        comment.updateContent(maximumContent);

        assertThatThrownBy(() -> new Comment(null, null, null, excessiveContent))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> comment.updateContent(excessiveContent))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(comment.getContent()).isEqualTo(maximumContent);
    }

    @Test
    void onlyAuthorCanEditActiveComment() {
        Member author = new Member("owner@test.com", "owner", "unused");
        ReflectionTestUtils.setField(author, "id", 1L);
        Comment comment = new Comment(null, author, null, "original");

        comment.validateAuthor(1L);
        assertThatThrownBy(() -> comment.validateAuthor(2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void deletedCommentRejectsEditingBeforeAccessingRemovedAuthor() {
        Member author = new Member("owner@test.com", "owner", "unused");
        Comment comment = new Comment(null, author, null, "original");
        comment.remove();

        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getAuthor()).isNull();
        assertThat(comment.getContent()).isEqualTo("삭제된 댓글입니다");
        assertThatThrownBy(() -> comment.validateAuthor(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_DELETED));
    }
}

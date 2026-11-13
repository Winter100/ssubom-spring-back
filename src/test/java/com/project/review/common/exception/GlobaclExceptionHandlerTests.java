package com.project.review.common.exception;

import com.project.review.member.exception.DuplicateMemberException;
import com.project.review.member.exception.MemberNotFoundException;
import com.project.review.member.exception.RefreshTokenException;
import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.exception.ReviewNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobaclExceptionHandlerTests {

    private final FailureController controller = new FailureController();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void returnsMessageAndFieldErrorsForInvalidReview() throws Exception {
        mvc.perform(post("/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" ","product":"키보드","body":{"type":"doc","content":[]},"rating":6,"categoryId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')].message", hasItem("제목은 필수입니다.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'rating')].message", hasItem("평점은 5 이하여야 합니다.")));
    }

    @Test
    void returnsIndexedFieldForInvalidListElement() throws Exception {
        mvc.perform(post("/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"후기","product":"키보드","body":{"type":"doc","content":[]},
                                 "rating":4,"categoryId":1,"mainImageKey":"reviews/main","subImageKeys":[" "]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("subImageKeys[0]"))
                .andExpect(jsonPath("$.errors[0].message").value("서브 이미지 키는 비어 있을 수 없습니다."));
    }

    @Test
    void returnsValidationPropertyForInvalidContentDocument() throws Exception {
        mvc.perform(post("/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"후기","product":"키보드","body":{"type":"paragraph","content":[]},
                                 "rating":4,"categoryId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.errors[0].field").value("contentDocumentValid"))
                .andExpect(jsonPath("$.errors[0].message").value("내용은 type이 doc이고 content가 배열인 JSON 문서여야 합니다."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{\"rating\":\"높음\"}"})
    void returnsMessageAndEmptyErrorsForUnreadableBody(String body) throws Exception {
        mvc.perform(post("/validation").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 본문이 없거나 JSON 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void returnsParameterFieldForTypeMismatch() throws Exception {
        mvc.perform(get("/parameters/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 파라미터 'id'의 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("요청 파라미터 'id'의 형식이 올바르지 않습니다."));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void preservesStatusAndMessageWithEmptyErrors(RuntimeException failure, int status, String message) throws Exception {
        controller.failure = failure;
        mvc.perform(get("/failure"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    private static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new BusinessException(ErrorCode.CATEGORY_NOT_FOUND), 404, "카테고리를 찾을 수 없습니다."),
                Arguments.of(new ReviewNotFoundException("리뷰 없음"), 404, "리뷰 없음"),
                Arguments.of(new MemberNotFoundException(), 404, "회원이 존재하지 않습니다."),
                Arguments.of(new IllegalArgumentException("잘못된 입력"), 400, "잘못된 입력"),
                Arguments.of(new DuplicateMemberException("중복 회원"), 409, "중복 회원"),
                Arguments.of(new BadCredentialsException("internal"), 401, "아이디 또는 비밀번호가 틀렸습니다."),
                Arguments.of(new OptimisticLockException(), 409, ErrorCode.REVIEW_UPDATE_CONFLICT.getMessage()),
                Arguments.of(new OptimisticLockingFailureException("conflict"), 409, ErrorCode.REVIEW_UPDATE_CONFLICT.getMessage()),
                Arguments.of(new DataIntegrityViolationException("internal"), 500, "서버 오류가 발생했습니다.")
        );
    }

    @Test
    void preservesRefreshCookieClearingAndCacheControl() throws Exception {
        controller.failure = new RefreshTokenException(ErrorCode.REFRESH_INVALID);
        mvc.perform(get("/failure"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(cookie().value("refreshToken", ""))
                .andExpect(cookie().path("refreshToken", "/api/v1/auth"))
                .andExpect(jsonPath("$.message").value(ErrorCode.REFRESH_INVALID.getMessage()))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @RestController
    static class FailureController {
        private RuntimeException failure;

        @PostMapping("/validation")
        void validate(@Valid @RequestBody ReviewRequest request) {
        }

        @GetMapping("/parameters/{id}")
        void parameter(@PathVariable("id") Long id) {
        }

        @GetMapping("/failure")
        void fail() {
            throw failure;
        }
    }
}

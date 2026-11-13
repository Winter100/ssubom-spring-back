package com.project.review.review;

import com.project.review.common.exception.GlobalExceptionHandler;
import com.project.review.auth.service.TokenService;
import com.project.review.review.controller.ReviewController;
import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.dtos.ReviewUpdateRequest;
import com.project.review.review.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static com.project.review.review.ReviewContentFixtures.textDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewRequestValidationTests {

    private ReviewService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ReviewService.class);
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.extractMemberId(null)).thenReturn(1L);
        mvc = MockMvcBuilders.standaloneSetup(new ReviewController(service, tokenService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @MethodSource("validImageCombinations")
    void acceptsImagesWithMainOrWithoutSubImages(String main, String sub) throws Exception {
        mvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(main, sub)))
                .andExpect(status().isCreated());

        verify(service).create(any(ReviewRequest.class), eq(1L));
    }

    private static Stream<Arguments> validImageCombinations() {
        return Stream.of(
                Arguments.of("null", "null"),
                Arguments.of("null", "[]"),
                Arguments.of("\"reviews/main\"", "null"),
                Arguments.of("\"reviews/main\"", "[]"),
                Arguments.of("\"reviews/main\"", "[\"reviews/sub\"]")
        );
    }

    @Test
    void acceptsSubImagesWithoutMain() throws Exception {
        mvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("null", "[\"reviews/sub\"]")))
                .andExpect(status().isCreated());

        verify(service).create(any(ReviewRequest.class), eq(1L));
    }

    @Test
    void excludesValidationPropertyFromJson() {
        ReviewRequest request = new ReviewRequest("제목", "상품", textDocument("내용"), BigDecimal.ONE,
                1L, "reviews/main", List.of("reviews/sub"), null, null, null, null);

        String json = JsonMapper.builder().build().writeValueAsString(request);

        assertThat(json).doesNotContain("imageCompositionValid", "contentDocumentValid");
    }

    @Test
    void bindsJsonDocumentForCreateAndUpdate() throws Exception {
        String body = requestBody("null", "null");
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/reviews/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        ArgumentCaptor<ReviewRequest> createRequest = ArgumentCaptor.forClass(ReviewRequest.class);
        ArgumentCaptor<ReviewUpdateRequest> updateRequest = ArgumentCaptor.forClass(ReviewUpdateRequest.class);
        verify(service).create(createRequest.capture(), eq(1L));
        verify(service).update(eq(1L), updateRequest.capture(), eq(1L));
        assertThat(createRequest.getValue().content()).isEqualTo(textDocument("내용"));
        assertThat(updateRequest.getValue().content()).isEqualTo(textDocument("내용"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "\"일반 문자열\"", "[]", "{}",
            "{\"type\":\"paragraph\",\"content\":[]}",
            "{\"type\":\"doc\",\"content\":{}}",
            "{\"type\":\"doc\"}"
    })
    void rejectsInvalidDocumentForCreateAndUpdate(String content) throws Exception {
        String body = """
                {"product":"test product","title":"제목","content":%s,"rating":4.5,"categoryId":1}
                """.formatted(content);
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/reviews/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingDocumentForCreateAndUpdate() throws Exception {
        String body = """
                {"product":"test product","title":"제목","rating":4.5,"categoryId":1}
                """;
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/reviews/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private String requestBody(String main, String sub) {
        return """
                {"product":"test product","title":"제목","content":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"내용"}]}]},"rating":4.5,"categoryId":1,
                 "mainImageKey":%s,"subImageKeys":%s}
                """.formatted(main, sub);
    }
}

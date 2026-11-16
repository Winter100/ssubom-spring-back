package com.project.review.storage;

import com.project.review.common.exception.GlobalExceptionHandler;
import com.project.review.auth.service.TokenService;
import com.project.review.member.entities.Member;
import com.project.review.member.service.MemberService;
import com.project.review.storage.controller.StorageController;
import com.project.review.storage.dtos.ImageUploadBatchRequest;
import com.project.review.storage.dtos.ImageUploadRequest;
import com.project.review.storage.dtos.ImageUploadResponse;
import com.project.review.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StorageUploadValidationTests {

    private StorageService storageService;
    private Member member;
    private MockMvc mvc;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        TokenService tokenService = mock(TokenService.class);
        MemberService memberService = mock(MemberService.class);
        member = Member.create("upload@test.com", "uploader", "unused");
        when(memberService.findById(1L)).thenReturn(member);
        when(tokenService.extractMemberId(null)).thenReturn(1L);
        mvc = MockMvcBuilders.standaloneSetup(new StorageController(storageService, tokenService, memberService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1, 0, 5_242_881})
    void rejectsInvalidFileSizeBeforeIssuingUrl(Long fileSize) throws Exception {
        ImageUploadBatchRequest request = new ImageUploadBatchRequest(
                List.of(new ImageUploadRequest("test.png", "image/png", fileSize, "client-1")));

        mvc.perform(post("/api/v1/storage/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(storageService);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 5_242_880})
    void acceptsFileSizeWithinLimit(long fileSize) throws Exception {
        ImageUploadBatchRequest request = new ImageUploadBatchRequest(
                List.of(new ImageUploadRequest("test.png", "image/png", fileSize, "client-1")));
        when(storageService.uploadUrls(request, member))
                .thenReturn(List.of(new ImageUploadResponse("test.png", "https://example.com/upload", "reviews/test", "client-1")));

        mvc.perform(post("/api/v1/storage/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(storageService).uploadUrls(request, member);
    }
}

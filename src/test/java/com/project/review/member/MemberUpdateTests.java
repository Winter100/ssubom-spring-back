package com.project.review.member;

import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.dtos.MemberUpdateRequest;
import com.project.review.member.dtos.MemberResponse;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.member.service.MemberService;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import com.project.review.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:member-update;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.access-token-expires-minutes=15", "security.jwt.refresh-token-expires-days=7",
        "storage.endpoint=https://example.com", "storage.region=auto", "storage.bucket=test-bucket",
        "storage.access-key=test-access-key", "storage.secret-key=test-secret-key",
        "storage.public-base-url=https://images.example.com", "storage.image-cleanup.enabled=false"
})
@AutoConfigureMockMvc
class MemberUpdateTests {
    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired MemberService service;
    @Autowired MemberRepository members;
    @Autowired ReviewImageDeleteRepository deletions;
    @MockitoBean StorageService storage;
    @MockitoBean Clock clock;
    private Member member;

    @BeforeEach
    void setUp() {
        deletions.deleteAll();
        members.deleteAll();
        when(clock.instant()).thenReturn(NOW);
        member = members.saveAndFlush(Member.create("member@example.com", "original", "unused"));
    }

    @Test
    void firstChangePersistsNicknameAndTimestamp() {
        service.updateMe(member.getId(), new MemberUpdateRequest("new-name", null));

        Member updated = members.findById(member.getId()).orElseThrow();
        assertThat(updated.getNickname()).isEqualTo("new-name");
        assertThat(updated.getNicknameChangedAt()).isEqualTo(NOW);
        assertThat(MemberResponse.from(updated).nicknameChangedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsChangeJustBeforeSevenDays() {
        setLastChange(NOW.minus(Duration.ofDays(7)).plusSeconds(1));

        assertThatThrownBy(() -> service.updateMe(member.getId(), new MemberUpdateRequest("new-name", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NICKNAME_CHANGE_NOT_ALLOWED));
        assertThat(members.findById(member.getId()).orElseThrow().getNickname()).isEqualTo("original");
    }

    @Test
    void allowsChangeExactlySevenDaysLater() {
        setLastChange(NOW.minus(Duration.ofDays(7)));

        service.updateMe(member.getId(), new MemberUpdateRequest("new-name", null));

        assertThat(members.findById(member.getId()).orElseThrow().getNicknameChangedAt()).isEqualTo(NOW);
    }

    @Test
    void sameNicknameAllowsRepeatedImageChangesDuringCooldown() {
        setLastChange(NOW.minusSeconds(60));

        service.updateMe(member.getId(), new MemberUpdateRequest("original", "image-a"));
        service.updateMe(member.getId(), new MemberUpdateRequest(null, "image-b"));

        Member updated = members.findById(member.getId()).orElseThrow();
        assertThat(updated.getProfileImage()).isEqualTo("image-b");
        assertThat(updated.getNicknameChangedAt()).isEqualTo(NOW.minusSeconds(60));
        verify(storage).attachIfPending(List.of("image-a"), member.getId());
        verify(storage).attachIfPending(List.of("image-b"), member.getId());
        assertThat(deletions.findAll()).singleElement()
                .satisfies(deletion -> assertThat(deletion.getImageKey()).isEqualTo("image-a"));
    }

    @Test
    void imageFailureRollsBackNicknameAndCooldown() {
        doThrow(new BusinessException(ErrorCode.IMAGE_UPLOAD_CONFLICT))
                .when(storage).attachIfPending(List.of("image-a"), member.getId());

        assertThatThrownBy(() -> service.updateMe(member.getId(), new MemberUpdateRequest("new-name", "image-a")))
                .isInstanceOf(BusinessException.class);

        Member updated = members.findById(member.getId()).orElseThrow();
        assertThat(updated.getNickname()).isEqualTo("original");
        assertThat(updated.getNicknameChangedAt()).isNull();
        assertThat(updated.getProfileImage()).isNull();
    }

    @Test
    void memberApiReturnsNicknameChangeTimestampAfterSaving() throws Exception {
        mvc.perform(get("/api/v1/member")
                        .with(jwt().jwt(token -> token.subject(member.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nicknameChangedAt").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(post("/api/v1/member")
                        .with(jwt().jwt(token -> token.subject(member.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickName\":\"new-name\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/member")
                        .with(jwt().jwt(token -> token.subject(member.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("new-name"))
                .andExpect(jsonPath("$.nicknameChangedAt").value(NOW.toString()));
    }

    @Test
    void memberApiRejectsNicknameDuringCooldownButAllowsImageOnlyUpdate() throws Exception {
        Instant changedAt = NOW.minusSeconds(60);
        setLastChange(changedAt);

        mvc.perform(post("/api/v1/member")
                        .with(jwt().jwt(token -> token.subject(member.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickName\":\"blocked-name\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/member")
                        .with(jwt().jwt(token -> token.subject(member.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileImage\":\"reviews/profile\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/member")
                        .with(jwt().jwt(token -> token.subject(member.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("original"))
                .andExpect(jsonPath("$.profileImage").value("reviews/profile"))
                .andExpect(jsonPath("$.nicknameChangedAt").value(changedAt.toString()));
    }
    private void setLastChange(Instant changedAt) {
        member.changeName("original", changedAt);
        member = members.saveAndFlush(member);
    }
}

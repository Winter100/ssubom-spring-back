package com.project.review.member.service;

import com.project.review.auth.dtos.SignupRequest;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.dtos.MemberResponse;
import com.project.review.member.dtos.MemberUpdateRequest;
import com.project.review.member.entities.Member;
import com.project.review.member.exception.DuplicateMemberException;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.entities.ReviewImageDelete;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import com.project.review.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final ReviewImageDeleteRepository reviewImageDeleteRepository;
    private final Clock imageDeletionClock;

    @Transactional
    public MemberResponse create(SignupRequest request) {

        existsEmail(request.email());
        existsNickname(request.nickname());

        Member member = Member.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build();

        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public void updateMe(Long memberId, MemberUpdateRequest memberUpdateRequest) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        String updateNickname = memberUpdateRequest.nickName();
        String profileImage = memberUpdateRequest.profileImage();

        // 닉네임 변경
        if (updateNickname != null
                && !updateNickname.isBlank()
                && !member.getNickname().equals(updateNickname)) {
            Instant now = imageDeletionClock.instant();
            Instant nicknameChangedAt = member.getNicknameChangedAt();
            if (nicknameChangedAt != null && now.isBefore(nicknameChangedAt.plus(Duration.ofDays(7)))) {
                throw new BusinessException(ErrorCode.NICKNAME_CHANGE_NOT_ALLOWED);
            }
            existsNickname(updateNickname);
            member.changeName(updateNickname, now);
        }

        if (profileImage != null
                && !profileImage.isBlank()
                && !Objects.equals(member.getProfileImage(), profileImage)
        ) {
            storageService.validateImagesForReview(List.of(memberUpdateRequest.profileImage()), member.getId());
            storageService.attachIfPending(List.of(profileImage), member.getId());

            if (member.getProfileImage() != null) {
                reviewImageDeleteRepository.save(new ReviewImageDelete(member.getProfileImage()));
            }
            member.changeProfileImage(profileImage);
        }
    }

    @Transactional(readOnly = true)
    public Member findByEmail(String email) {
        return memberRepository.findByEmail(email.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Member findById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void existsEmail(String email) {
        if (memberRepository.existsByEmail(email.toLowerCase(Locale.ROOT))) {
            throw new DuplicateMemberException("이미 가입된 이메일 입니다.");
        }
    }

    private void existsNickname(String nickname) {
        if (memberRepository.existsByNickname(nickname)) {
            throw new DuplicateMemberException("이미 생성된 닉네임 입니다.");
        }
    }

}

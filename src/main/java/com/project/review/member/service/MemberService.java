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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final ReviewImageDeleteRepository reviewImageDeleteRepository;
    private final Clock clock;
    private final TransactionTemplate transactions;

    @Transactional
    public MemberResponse create(SignupRequest request) {

        existsEmail(request.email());
        existsNickname(request.nickname());

        Member member = Member.create(
                request.email(),
                request.nickname(),
                passwordEncoder.encode(request.password())
        );

        return MemberResponse.from(memberRepository.save(member));
    }

    public void updateMe(Long memberId, MemberUpdateRequest req) {
        // 외부 스토리지 검증을 마친 후에만 회원 행을 잠급니다.
        Member before = findById(memberId);
        if (req.hasProfileImage() && !before.isSameProfileImage(req.profileImage())) {
            storageService.validateImagesForReview(List.of(req.profileImage()), memberId);
        }
        transactions.executeWithoutResult(status -> updateMember(memberId, req));
    }

    private void updateMember(Long memberId, MemberUpdateRequest req) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 닉네임 변경 (7일) 제한
        if (req.hasName() && !member.isSameName(req.nickName())) {
            existsNickname(req.nickName());
            Instant now = clock.instant();
            member.changeName(req.nickName(), now);
        }

        // 프로필 이미지 변경
        if (req.hasProfileImage() && !member.isSameProfileImage(req.profileImage())) {
            storageService.attachIfPending(List.of(req.profileImage()), member.getId());

            if (member.hasProfileImage()) {
                reviewImageDeleteRepository.save(new ReviewImageDelete(member.getProfileImage()));
            }
            member.changeProfileImage(req.profileImage());
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

    // 회원을 참조하는 쓰기 트랜잭션은 탈퇴 삭제와 같은 행 잠금을 사용합니다.
    @Transactional(propagation = Propagation.MANDATORY)
    public Member findByIdForWrite(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTHENTICATION));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Member findByIdForContentWrite(Long memberId) {
        Member member = findByIdForWrite(memberId);
        member.validateContentWriteAllowed();
        return member;
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

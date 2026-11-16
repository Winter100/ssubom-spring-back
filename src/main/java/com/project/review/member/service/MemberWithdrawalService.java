package com.project.review.member.service;

import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.dtos.WithdrawalResponse;
import com.project.review.member.entities.Member;
import com.project.review.review.service.ImageDeletionTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {
    private final MemberService members;
    private final PasswordEncoder passwords;
    private final Clock clock;

    @Transactional
    public WithdrawalResponse request(Long memberId, String password) {
        Member member = members.findByIdForWrite(memberId);
        if (!passwords.matches(password, member.getPassword())) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_PASSWORD_MISMATCH);
        }
        Instant now = clock.instant();
        member.requestWithdrawal(now, ImageDeletionTime.nextMidnight(now.plus(Duration.ofHours(24))));
        return WithdrawalResponse.from(member);
    }

    @Transactional(readOnly = true)
    public WithdrawalResponse find(Long memberId) {
        return WithdrawalResponse.from(members.findById(memberId));
    }

    @Transactional
    public void cancel(Long memberId) {
        members.findByIdForWrite(memberId).cancelWithdrawal();
    }
}

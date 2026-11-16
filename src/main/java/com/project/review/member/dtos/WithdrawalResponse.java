package com.project.review.member.dtos;

import com.project.review.member.entities.Member;
import java.time.Instant;

public record WithdrawalResponse(Instant requestedAt, Instant scheduledAt) {
    public static WithdrawalResponse from(Member member) {
        return new WithdrawalResponse(member.getWithdrawalRequestedAt(), member.getWithdrawalScheduledAt());
    }
}

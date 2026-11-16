package com.project.review.member.service;

import com.project.review.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "member.withdrawal-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class MemberWithdrawalCleanup {
    private final MemberRepository members;
    private final MemberDeletionService deletionService;
    private final Clock clock;
    private final ReentrantLock executionLock = new ReentrantLock();

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @EventListener(ApplicationReadyEvent.class)
    public void deleteDueMembers() {
        if (!executionLock.tryLock()) return;
        int deleted = 0;
        int failed = 0;
        Instant cutoff = clock.instant();
        try {
            long afterId = 0;
            while (true) {
                List<Long> batch = members.findDueWithdrawalIds(cutoff, afterId, PageRequest.of(0, 100));
                if (batch.isEmpty()) break;
                for (Long memberId : batch) {
                    try {
                        if (deletionService.deleteIfDue(memberId, cutoff)) deleted++;
                    } catch (RuntimeException exception) {
                        failed++;
                        log.error("회원 탈퇴 처리 실패: 회원 ID={}, 다음 실행에서 재시도합니다.", memberId, exception);
                    }
                }
                afterId = batch.getLast();
            }
        } catch (RuntimeException exception) {
            log.error("탈퇴 대상 조회 실패: 다음 실행에서 재시도합니다.", exception);
        } finally {
            log.info("회원 탈퇴 처리: 성공={}, 실패={}", deleted, failed);
            executionLock.unlock();
        }
    }
}

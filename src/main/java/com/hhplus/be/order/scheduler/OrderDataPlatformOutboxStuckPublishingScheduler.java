package com.hhplus.be.order.scheduler;

import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.model.OutboxStatus;
import com.hhplus.be.order.domain.repository.OrderDataPlatformOutboxRepository;
import com.hhplus.be.order.service.OrderOutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Stuck PUBLISHING 상태 처리 스케줄러
 *
 * 역할:
 * - PUBLISHING 상태로 일정 시간 이상 머물러 있는 Outbox 감지
 * - 프로세스가 전송 중 죽어서 상태 전이가 멈춘 경우 복구
 * - FAILED 상태로 변경하여 Retry Scheduler가 재시도하도록 함
 *
 * 실행 주기: 1분마다
 * Timeout 기준: 5분 (updatedAt 기준)
 *
 * 시나리오:
 * 1. Poller가 PENDING → PUBLISHING 변경
 * 2. Kafka 전송 중 프로세스 죽음
 * 3. PUBLISHING 상태로 stuck (5분 경과)
 * 4. 이 Scheduler가 FAILED로 변경
 * 5. Retry Scheduler가 PENDING으로 재시도
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDataPlatformOutboxStuckPublishingScheduler {
    private final OrderDataPlatformOutboxRepository outboxRepository;
    private final OrderOutboxPublisherService publisherService;
    private final Clock clock;

    // PUBLISHING 상태 timeout: 5분
    private static final int PUBLISHING_TIMEOUT_MINUTES = 5;

    /**
     * Stuck PUBLISHING 상태 처리 (1분마다 실행)
     *
     * 조건:
     * - 상태 = PUBLISHING
     * - updatedAt < 현재시각 - 5분
     *
     * 처리:
     * - FAILED 상태로 변경 (Retry Scheduler가 나중에 재처리)
     *
     * ShedLock 적용:
     * - lockAtMostFor: 최대 50초 (서버 장애 시 락 자동 해제)
     * - lockAtLeastFor: 최소 10초 (너무 자주 실행 방지)
     */
    @Scheduled(fixedDelay = 60_000) // 1분
    @SchedulerLock(
            name = "recoverStuckPublishing",
            lockAtMostFor = "50s",
            lockAtLeastFor = "10s"
    )
    public void recoverStuckPublishing() {
        try {
            log.debug("🔍 [Stuck PUBLISHING Scheduler] Stuck 상태 검사 시작");

            // timeout 기준 시각 (5분 전)
            Instant timeoutBefore = clock.instant().minus(PUBLISHING_TIMEOUT_MINUTES, ChronoUnit.MINUTES);

            // Stuck PUBLISHING 조회
            List<OrderDataPlatformOutbox> stuckOutboxes =
                    outboxRepository.findByStatusAndUpdatedAtBefore(
                            OutboxStatus.PUBLISHING,
                            timeoutBefore
                    );

            if (stuckOutboxes.isEmpty()) {
                log.debug("✅ [Stuck PUBLISHING Scheduler] Stuck 상태 없음");
                return;
            }

            log.warn("⚠️ [Stuck PUBLISHING Scheduler] Stuck 상태 발견 - count: {}", stuckOutboxes.size());

            // 각 Stuck Outbox를 FAILED로 변경
            int recoveredCount = 0;
            for (OrderDataPlatformOutbox outbox : stuckOutboxes) {
                try {
                    String errorMessage = String.format(
                            "PUBLISHING 상태 timeout (%d분 초과) - 프로세스 장애로 추정",
                            PUBLISHING_TIMEOUT_MINUTES
                    );

                    publisherService.markAsFailed(outbox, errorMessage);
                    recoveredCount++;

                    log.warn("🚑 [Stuck PUBLISHING Scheduler] Stuck 복구 - outboxId: {}, orderId: {}, updatedAt: {}",
                            outbox.getId(), outbox.getOrderId(), outbox.getUpdatedAt());

                } catch (Exception e) {
                    log.error("❌ [Stuck PUBLISHING Scheduler] 복구 실패 - outboxId: {}, orderId: {}",
                            outbox.getId(), outbox.getOrderId(), e);
                }
            }

            log.info("✅ [Stuck PUBLISHING Scheduler] 복구 완료 - 복구: {}/{}", recoveredCount, stuckOutboxes.size());

        } catch (Exception e) {
            log.error("❌ [Stuck PUBLISHING Scheduler] 스케줄러 오류", e);
        }
    }
}
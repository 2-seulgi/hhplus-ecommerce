package com.hhplus.be.order.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.model.OutboxStatus;
import com.hhplus.be.order.domain.repository.OrderDataPlatformOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 실패한 데이터 플랫폼 전송 재처리 스케줄러
 *
 * 역할:
 * - FAILED 상태의 Outbox 레코드를 주기적으로 조회
 * - 재처리 시도 (재시도 횟수 제한)
 * - 실패 시 재시도 횟수 증가 및 FAILED 유지
 *
 * 실행 주기: 10분마다
 * 재시도 조건:
 * - 상태가 FAILED
 * - 재시도 횟수 < 10
 * - 생성 후 5분 경과 (즉시 재시도 방지)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDataPlatformOutboxRetryScheduler {
    private final OrderDataPlatformOutboxRepository outboxRepository;
    private final Clock clock;

    private static final int MAX_RETRY_COUNT = 10;
    private static final int RETRY_DELAY_MINUTES = 5;

       /**
     * 실패한 Outbox 재처리 (10분마다 실행)
     *
     * ShedLock 적용:
     * - lockAtMostFor: 최대 9분 (서버 장애 시 락 자동 해제)
     * - lockAtLeastFor: 최소 30초 (너무 자주 실행 방지)
     * - 여러 인스턴스 환경에서 중복 실행 방지
     */
    @Scheduled(fixedDelay = 600_000) // 10분
    @SchedulerLock(
            name = "retryFailedDataPlatformOutbox",
            lockAtMostFor = "9m",
            lockAtLeastFor = "30s"
    )
    public void retryFailedOutbox() {
        try {
            log.debug("🔄 실패한 데이터 플랫폼 전송 재처리 시작");

            // 재처리 대상 조회
            Instant retryBefore = clock.instant().minus(RETRY_DELAY_MINUTES, ChronoUnit.MINUTES);
            List<OrderDataPlatformOutbox> failedOutboxes = outboxRepository
                    .findByStatusAndRetryCountLessThanAndCreatedAtBefore(
                            OutboxStatus.FAILED,
                            MAX_RETRY_COUNT,
                            retryBefore
                    );

            if (failedOutboxes.isEmpty()) {
                log.debug("재처리할 실패 레코드 없음");
                return;
            }

            log.info("재처리 대상 발견 - count: {}", failedOutboxes.size());

            // 각 실패 레코드 재처리
            int successCount = 0;
            int failCount = 0;

            for (OrderDataPlatformOutbox outbox : failedOutboxes) {
                boolean success = retryOutbox(outbox);
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            log.info("✅ 재처리 완료 - 성공: {}, 실패: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("재처리 스케줄러 오류", e);
        }
    }

    /**
     * 개별 Outbox 재처리
     *
     * @param outbox 재처리할 Outbox
     * @return 성공 여부
     */
    @Transactional
    protected boolean retryOutbox(OrderDataPlatformOutbox outbox) {
        try {
            Instant now = clock.instant();

            log.debug("재처리 시도 - outboxId: {}, orderId: {}, retryCount: {}",
                    outbox.getId(), outbox.getOrderId(), outbox.getRetryCount());

            // 재시도 횟수 증가
            outbox.incrementRetry(clock.instant());

            // 2) 다시 발행 대기 상태로 변경 (Poller가 다시 Kafka 발행하도록)
            outbox.markAsPending(now);

            outboxRepository.save(outbox);

            log.info("🔁 재발행 큐잉 완료 - outboxId: {}, orderId: {}, retryCount: {}",
                    outbox.getId(), outbox.getOrderId(), outbox.getRetryCount());

            return true;

        } catch (Exception e) {
            log.warn("❌ 재발행 큐잉 실패 - outboxId: {}, orderId: {}, error: {}",
                    outbox.getId(), outbox.getOrderId(), e.getMessage(), e);
            return false;
        }
    }
}
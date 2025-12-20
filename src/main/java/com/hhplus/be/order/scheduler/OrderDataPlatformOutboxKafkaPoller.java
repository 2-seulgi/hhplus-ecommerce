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

import java.util.List;

/**
 * Outbox Poller - PENDING 상태의 Outbox를 Kafka로 발행
 *
 * 역할:
 * - PENDING 상태의 Outbox 레코드를 주기적으로 조회
 * - OrderOutboxPublisherService에 발행 위임
 * - 배치 처리 및 성공/실패 집계
 *
 * 실행 주기: 5초마다 (빠른 응답을 위해)
 * 배치 크기: 100개씩
 *
 * 설계 개선:
 * - 트랜잭션 경계를 Service 레이어로 이동하여 명확성 확보
 * - Self-invocation 문제 해결 (별도 빈으로 분리)
 * - Poller는 배치 조회와 집계만 담당
 *
 * Transactional Outbox Pattern 구현
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDataPlatformOutboxKafkaPoller {
    private final OrderDataPlatformOutboxRepository outboxRepository;
    private final OrderOutboxPublisherService publisherService;

    /**
     * PENDING 상태의 Outbox를 Kafka로 발행 (5초마다 실행)
     *
     * ShedLock 적용:
     * - lockAtMostFor: 최대 4초 (서버 장애 시 락 자동 해제)
     * - lockAtLeastFor: 최소 1초 (너무 자주 실행 방지)
     * - 여러 인스턴스 환경에서 중복 실행 방지
     */
    @Scheduled(fixedDelay = 5_000) // 5초
    @SchedulerLock(
            name = "publishPendingOutboxToKafka",
            lockAtMostFor = "4s",
            lockAtLeastFor = "1s"
    )
    public void publishPendingOutboxToKafka() {
        try {
            log.debug("📤 [Outbox Poller] PENDING 상태 Outbox 발행 시작");

            // PENDING 상태 조회 (최대 100개)
            List<OrderDataPlatformOutbox> pendingOutboxes =
                    outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

            if (pendingOutboxes.isEmpty()) {
                return;
            }

            log.info("📤 [Outbox Poller] 발행 대상 발견 - count: {}", pendingOutboxes.size());

            // 각 Outbox를 서비스에 위임하여 발행
            int successCount = 0;
            int failCount = 0;

            for (OrderDataPlatformOutbox outbox : pendingOutboxes) {
                try {
                    // 서비스에 발행 위임 (트랜잭션 경계가 명확함)
                    publisherService.publish(outbox);
                    successCount++;

                } catch (Exception e) {
                    // 발행 실패 시 FAILED 상태로 마킹
                    // Retry Scheduler가 나중에 재시도함
                    publisherService.markAsFailed(outbox, e.getMessage());
                    failCount++;
                }
            }

            log.info("✅ [Outbox Poller] 발행 완료 - 성공: {}, 실패: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("❌ [Outbox Poller] 스케줄러 오류", e);
        }
    }
}
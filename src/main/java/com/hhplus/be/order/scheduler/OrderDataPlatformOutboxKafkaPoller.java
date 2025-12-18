package com.hhplus.be.order.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.model.OutboxStatus;
import com.hhplus.be.order.domain.repository.OrderDataPlatformOutboxRepository;
import com.hhplus.be.order.infrastructure.producer.OrderEventKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Outbox Poller - PENDING 상태의 Outbox를 Kafka로 발행
 *
 * 역할:
 * - PENDING 상태의 Outbox 레코드를 주기적으로 조회
 * - Kafka Producer를 통해 메시지 발행
 * - 성공 시 PUBLISHED 상태로 변경
 * - 실패 시 FAILED 상태로 변경
 *
 * 실행 주기: 5초마다 (빠른 응답을 위해)
 * 배치 크기: 100개씩
 *
 * Transactional Outbox Pattern 구현
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDataPlatformOutboxKafkaPoller {
    private final OrderDataPlatformOutboxRepository outboxRepository;
    private final OrderEventKafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private static final int BATCH_SIZE = 100;

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

            // 각 Outbox를 Kafka로 발행
            int successCount = 0;
            int failCount = 0;

            for (OrderDataPlatformOutbox outbox : pendingOutboxes) {
                boolean success = publishOutbox(outbox);
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            log.info("✅ [Outbox Poller] 발행 완료 - 성공: {}, 실패: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("❌ [Outbox Poller] 스케줄러 오류", e);
        }
    }

    /**
     * 개별 Outbox를 Kafka로 발행
     *
     * @param outbox 발행할 Outbox
     * @return 성공 여부
     */
    @Transactional
    protected boolean publishOutbox(OrderDataPlatformOutbox outbox) {
        try {
            log.debug("📤 [Outbox Poller] 발행 시도 - outboxId: {}, orderId: {}",
                    outbox.getId(), outbox.getOrderId());

            // JSON을 OrderConfirmedEvent로 역직렬화
            OrderConfirmedEvent event = objectMapper.readValue(
                    outbox.getOrderData(),
                    OrderConfirmedEvent.class
            );

            // Kafka로 발행
            kafkaProducer.sendOrderConfirmedEvent(event);

            // PUBLISHED 상태로 변경
            outbox.markAsPublished(clock.instant());
            outboxRepository.save(outbox);

            log.info("✅ [Outbox Poller] 발행 성공 - outboxId: {}, orderId: {}",
                    outbox.getId(), outbox.getOrderId());

            return true;

        } catch (Exception e) {
            log.error("❌ [Outbox Poller] 발행 실패 - outboxId: {}, orderId: {}, error: {}",
                    outbox.getId(), outbox.getOrderId(), e.getMessage(), e);

            // FAILED 상태로 변경
            String errorMsg = "Kafka 발행 실패: " + e.getMessage();
            outbox.markAsFailed(errorMsg, clock.instant());
            outboxRepository.save(outbox);

            return false;
        }
    }
}
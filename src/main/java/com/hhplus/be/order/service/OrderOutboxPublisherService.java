package com.hhplus.be.order.service;

import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.repository.OrderDataPlatformOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Outbox 상태 관리 서비스
 *
 * 책임:
 * - Outbox 상태 전이를 짧은 트랜잭션으로 처리
 * - DB 커넥션 점유 시간 최소화 (네트워크 대기를 트랜잭션 밖으로)
 *
 * 설계 개선:
 * - 기존: PENDING → (Kafka 전송 5초 대기) → PUBLISHED (1개 긴 트랜잭션)
 * - 개선: PENDING → PUBLISHING (짧은 TX) → Kafka 전송 (TX 밖) → PUBLISHED (짧은 TX)
 *
 * 효과:
 * - DB 커넥션 점유: 5초 → ~20ms (250배 감소)
 * - Kafka 장애 시 커넥션 풀 고갈 방지
 * - 다른 API 요청에 영향 최소화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOutboxPublisherService {

    private final OrderDataPlatformOutboxRepository outboxRepository;
    private final Clock clock;

    /**
     * Kafka 전송 시작 (PENDING → PUBLISHING)
     *
     * 짧은 트랜잭션 (~10ms):
     * - 상태만 변경하고 즉시 커밋
     * - DB 커넥션 점유 최소화
     *
     * @param outbox 전송 시작할 Outbox
     */
    @Transactional
    public void markAsPublishing(OrderDataPlatformOutbox outbox) {
        try {
            outbox.markAsPublishing(clock.instant());
            outboxRepository.save(outbox);

            log.debug("📤 [Outbox Publisher] 전송 시작 - outboxId: {}, orderId: {}, status: PUBLISHING",
                    outbox.getId(), outbox.getOrderId());

        } catch (Exception e) {
            log.error("❌ [Outbox Publisher] PUBLISHING 상태 변경 실패 - outboxId: {}, orderId: {}",
                    outbox.getId(), outbox.getOrderId(), e);
            throw e;
        }
    }

    /**
     * Kafka 전송 성공 (PUBLISHING → PUBLISHED)
     *
     * 짧은 트랜잭션 (~10ms):
     * - Kafka ACK 받은 후 상태만 변경
     * - DB 커넥션 점유 최소화
     *
     * @param outbox 전송 성공한 Outbox
     */
    @Transactional
    public void markAsPublished(OrderDataPlatformOutbox outbox) {
        try {
            outbox.markAsPublished(clock.instant());
            outboxRepository.save(outbox);

            log.info("✅ [Outbox Publisher] 전송 완료 - outboxId: {}, orderId: {}, status: PUBLISHED",
                    outbox.getId(), outbox.getOrderId());

        } catch (Exception e) {
            log.error("❌ [Outbox Publisher] PUBLISHED 상태 변경 실패 - outboxId: {}, orderId: {}",
                    outbox.getId(), outbox.getOrderId(), e);
            throw e;
        }
    }

    /**
     * Kafka 전송 실패 (PUBLISHING/PENDING → FAILED)
     *
     * 짧은 트랜잭션 (~10ms):
     * - 전송 실패 시 에러 메시지와 함께 FAILED로 변경
     *
     * @param outbox 실패한 Outbox
     * @param errorMessage 에러 메시지
     */
    @Transactional
    public void markAsFailed(OrderDataPlatformOutbox outbox, String errorMessage) {
        try {
            outbox.markAsFailed(errorMessage, clock.instant());
            outboxRepository.save(outbox);

            log.warn("⚠️ [Outbox Publisher] 전송 실패 - outboxId: {}, orderId: {}, status: FAILED, error: {}",
                    outbox.getId(), outbox.getOrderId(), errorMessage);

        } catch (Exception e) {
            log.error("❌ [Outbox Publisher] FAILED 상태 변경 실패 - outboxId: {}, orderId: {}",
                    outbox.getId(), outbox.getOrderId(), e);
        }
    }
}
package com.hhplus.be.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.repository.OrderDataPlatformOutboxRepository;
import com.hhplus.be.order.infrastructure.producer.OrderEventKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Outbox 발행 서비스
 *
 * 책임:
 * - Outbox 한 건의 발행 처리 (비즈니스 단위)
 * - Kafka 동기 전송과 DB 상태 업데이트를 한 트랜잭션으로 처리
 * - 트랜잭션 경계를 명확히 정의
 *
 * 설계 이유:
 * - Poller에서 직접 @Transactional을 쓰면 self-invocation 문제 발생
 * - 별도 빈으로 분리하여 Spring AOP 프록시가 정상 동작하도록 함
 * - 트랜잭션 경계가 명확해져 테스트와 유지보수가 용이함
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOutboxPublisherService {

    private final OrderDataPlatformOutboxRepository outboxRepository;
    private final OrderEventKafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Outbox를 Kafka로 발행하고 상태를 PUBLISHED로 변경
     *
     * 트랜잭션 처리:
     * - Kafka 동기 전송 (최대 5초 타임아웃)
     * - 전송 성공 시: PUBLISHED 상태로 업데이트
     * - 전송 실패 시: 예외 발생 → 트랜잭션 롤백 → PENDING 상태 유지 → 다음 Poller 주기에 재시도
     *
     * DB 커넥션 점유:
     * - Kafka 전송 시간(최대 5초) 동안 DB 커넥션 점유
     * - 실용적으로는 대부분 100ms 이내 완료되므로 허용 가능
     *
     * @param outbox 발행할 Outbox
     * @throws OrderEventKafkaProducer.KafkaTransmissionException Kafka 전송 실패 시
     * @throws Exception 역직렬화 실패 등 기타 오류
     */
    @Transactional
    public void publish(OrderDataPlatformOutbox outbox) {
        try {
            log.debug("📤 [Outbox Publisher] 발행 시도 - outboxId: {}, orderId: {}",
                    outbox.getId(), outbox.getOrderId());

            // 1. JSON을 OrderConfirmedEvent로 역직렬화
            OrderConfirmedEvent event = objectMapper.readValue(
                    outbox.getOrderData(),
                    OrderConfirmedEvent.class
            );

            // 2. Kafka로 발행 (동기 대기 - Kafka ACK 받을 때까지 블로킹)
            kafkaProducer.sendOrderConfirmedEventSync(event);

            // 3. Kafka 전송 성공 확인 후 PUBLISHED 상태로 변경
            outbox.markAsPublished(clock.instant());
            outboxRepository.save(outbox);

            log.info("✅ [Outbox Publisher] 발행 성공 - outboxId: {}, orderId: {}",
                    outbox.getId(), outbox.getOrderId());

        } catch (OrderEventKafkaProducer.KafkaTransmissionException e) {
            // Kafka 전송 실패 (타임아웃, 브로커 장애 등)
            log.error("❌ [Outbox Publisher] Kafka 전송 실패 - outboxId: {}, orderId: {}, error: {}",
                    outbox.getId(), outbox.getOrderId(), e.getMessage());

            // 예외를 다시 던져서 트랜잭션 롤백
            // → Outbox는 PENDING 상태로 남아 다음 Poller 주기에 재시도
            throw e;

        } catch (Exception e) {
            // 역직렬화 실패 등 기타 오류
            log.error("❌ [Outbox Publisher] 발행 실패 - outboxId: {}, orderId: {}, error: {}",
                    outbox.getId(), outbox.getOrderId(), e.getMessage());

            // 예외를 다시 던져서 트랜잭션 롤백
            throw new RuntimeException("Outbox 발행 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Outbox 발행 실패 처리 (별도 트랜잭션)
     *
     * publish() 실패 시 Poller에서 이 메서드를 호출하여
     * Outbox를 FAILED 상태로 명시적으로 표시합니다.
     *
     * @param outbox 실패한 Outbox
     * @param errorMessage 에러 메시지
     */
    @Transactional
    public void markAsFailed(OrderDataPlatformOutbox outbox, String errorMessage) {
        try {
            outbox.markAsFailed(errorMessage, clock.instant());
            outboxRepository.save(outbox);

            log.warn("⚠️ [Outbox Publisher] FAILED 상태로 변경 - outboxId: {}, orderId: {}, error: {}",
                    outbox.getId(), outbox.getOrderId(), errorMessage);

        } catch (Exception e) {
            log.error("❌ [Outbox Publisher] FAILED 상태 변경 실패 - outboxId: {}, orderId: {}",
                    outbox.getId(), outbox.getOrderId(), e);
        }
    }
}
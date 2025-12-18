package com.hhplus.be.order.listener;

import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.repository.OrderDataPlatformOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 주문 완료 이벤트 Kafka Consumer (Presentation 레이어)
 *
 * 역할:
 * - order.confirmed 토픽에서 메시지 수신
 * - 외부 데이터 플랫폼으로 전송
 * - Outbox 상태를 SUCCESS로 업데이트
 *
 * 실패 처리:
 * - 재시도: 3회 (Spring Kafka 설정)
 * - 최종 실패 시: DLQ(Dead Letter Queue)로 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDataPlatformKafkaConsumer {
    private final OrderDataPlatformOutboxRepository outboxRepository;
    private final Clock clock;

    /**
     * order.confirmed 토픽 구독
     *
     * @param event 주문 완료 이벤트
     * @param ack 수동 커밋용 Acknowledgment
     */
    @KafkaListener(
            topics = "order.confirmed",
            groupId = "ecommerce-order-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeOrderConfirmed(OrderConfirmedEvent event, Acknowledgment ack) {
        try {
            log.info("📥 [Kafka Consumer] 주문 완료 이벤트 수신 - orderId: {}, userId: {}",
                    event.getOrderId(), event.getUserId());

            // 실제 데이터 플랫폼 전송 로직
            sendToDataPlatform(event);

            // Outbox 상태 업데이트 (PUBLISHED → SUCCESS)
            OrderDataPlatformOutbox outbox = outboxRepository.findByOrderId(event.getOrderId())
                    .orElseThrow(() -> new IllegalStateException("Outbox not found: " + event.getOrderId()));

            outbox.markAsSuccess(clock.instant());
            outboxRepository.save(outbox);

            // 수동 커밋 (메시지 처리 완료 확인)
            ack.acknowledge();

            log.info("✅ [Kafka Consumer] 처리 완료 - orderId: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("❌ [Kafka Consumer] 처리 실패 - orderId: {}, error: {}",
                    event.getOrderId(), e.getMessage(), e);

            // 예외를 다시 던져서 Kafka 재시도 메커니즘 작동
            throw new RuntimeException("주문 완료 이벤트 처리 실패", e);
        }
    }

    /**
     * 실제 데이터 플랫폼으로 전송
     * TODO: RestTemplate 또는 WebClient로 실제 API 호출
     */
    private void sendToDataPlatform(OrderConfirmedEvent event) {
        try {
            log.info("📤 [Data Platform] 전송 중 - orderId: {}, userId: {}, items: {}",
                    event.getOrderId(), event.getUserId(), event.getOrderItems().size());

            // Mock 전송 (실제 환경에서는 API 호출)
            Thread.sleep(50);

            log.info("✅ [Data Platform] 전송 완료 - orderId: {}", event.getOrderId());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("전송 중단됨", e);
        }
    }
}
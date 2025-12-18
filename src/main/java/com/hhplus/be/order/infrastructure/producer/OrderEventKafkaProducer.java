package com.hhplus.be.order.infrastructure.producer;

import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.order.domain.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Producer - 주문 이벤트를 Kafka로 발행
 *
 * Infrastructure 레이어 (외부 시스템과의 통신)
 * - Kafka 브로커로 메시지 전송
 * - 전송 결과 로깅
 * - 비동기 전송 (논블로킹)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventKafkaProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 토픽 이름 상수
    private static final String TOPIC_ORDER_CONFIRMED = "order.confirmed";
    private static final String TOPIC_ORDER_STATUS_CHANGED = "order.status.changed";

    /**
     * 주문 완료 이벤트를 Kafka로 발행
     *
     * @param event 주문 완료 이벤트
     */
    public void sendOrderConfirmedEvent(OrderConfirmedEvent event) {
        String key = event.getOrderId().toString();  // 주문 ID를 키로 사용 (같은 주문은 같은 파티션으로)

        log.info("📤 [Kafka Producer] 주문 완료 이벤트 발행 시작 - orderId: {}, topic: {}",
                event.getOrderId(), TOPIC_ORDER_CONFIRMED);

        sendEvent(TOPIC_ORDER_CONFIRMED, key, event, "주문 완료");
    }

    /**
     * 주문 상태 변경 이벤트를 Kafka로 발행
     *
     * @param event 주문 상태 변경 이벤트
     */
    public void sendOrderStatusChangedEvent(OrderStatusChangedEvent event) {
        String key = event.getOrderId().toString();  // 주문 ID를 키로 사용

        log.info("📤 [Kafka Producer] 주문 상태 변경 이벤트 발행 시작 - orderId: {}, {} → {}, topic: {}",
                event.getOrderId(), event.getPreviousStatus(), event.getNewStatus(), TOPIC_ORDER_STATUS_CHANGED);

        sendEvent(TOPIC_ORDER_STATUS_CHANGED, key, event, "주문 상태 변경");
    }

    /**
     * 공통 메시지 전송 로직
     */
    private void sendEvent(String topic, String key, Object event, String eventType) {
        try {
            // Kafka로 메시지 전송 (비동기)
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topic, key, event);

            // 전송 결과 콜백 처리
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    // 성공
                    log.info("✅ [Kafka Producer] {} 이벤트 전송 성공 - key: {}, partition: {}, offset: {}",
                            eventType,
                            key,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    // 실패
                    log.error("❌ [Kafka Producer] {} 이벤트 전송 실패 - key: {}, error: {}",
                            eventType, key, ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("❌ [Kafka Producer] {} 이벤트 발행 중 예외 발생 - key: {}, error: {}",
                    eventType, key, e.getMessage(), e);
            throw new RuntimeException("Kafka 메시지 발행 실패: " + eventType, e);
        }
    }
}
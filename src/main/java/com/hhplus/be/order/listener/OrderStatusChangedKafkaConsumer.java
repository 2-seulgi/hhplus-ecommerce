package com.hhplus.be.order.listener;

import com.hhplus.be.order.domain.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 주문 상태 변경 이벤트 Kafka Consumer (Presentation 레이어)
 *
 * 역할:
 * - order.status.changed 토픽에서 메시지 수신
 * - 로그 저장, 모니터링, 감사(Audit)
 *
 * 장점:
 * - 로그 저장 실패가 주문을 막지 않음
 * - 모든 상태 변경 이력 중앙화
 * - 나중에 감사 서비스로 분리 가능
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusChangedKafkaConsumer {

    /**
     * order.status.changed 토픽 구독
     *
     * @param event 주문 상태 변경 이벤트
     * @param ack 수동 커밋용 Acknowledgment
     */
    @KafkaListener(
            topics = "order.status.changed",
            groupId = "ecommerce-order-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderStatusChanged(OrderStatusChangedEvent event, Acknowledgment ack) {
        try {
            log.info("📥 [Kafka Consumer] 주문 상태 변경 이벤트 수신 - orderId: {}, {} → {}, reason: {}",
                    event.getOrderId(),
                    event.getPreviousStatus(),
                    event.getNewStatus(),
                    event.getReason());

            // 로그 저장 로직
            saveStatusChangeLog(event);

            // 수동 커밋
            ack.acknowledge();

            log.info("✅ [Kafka Consumer] 상태 변경 로그 저장 완료 - orderId: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("❌ [Kafka Consumer] 상태 변경 로그 저장 실패 - orderId: {}, error: {}",
                    event.getOrderId(), e.getMessage(), e);

            // 로그 저장 실패는 치명적이지 않으므로 커밋
            // (필요하면 재시도 로직 추가 가능)
            ack.acknowledge();
        }
    }

    /**
     * 상태 변경 로그 저장
     * TODO: 별도 로그 테이블에 저장하거나 외부 로그 시스템으로 전송
     */
    private void saveStatusChangeLog(OrderStatusChangedEvent event) {
        // 현재는 로그만 출력
        log.info("📝 [Status Log] orderId={}, userId={}, {}→{}, reason={}, changedAt={}",
                event.getOrderId(),
                event.getUserId(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getReason(),
                event.getChangedAt());

        // TODO: 실제 환경에서는 다음 중 하나 선택
        // 1. 별도 audit_log 테이블에 저장
        // 2. Elasticsearch에 저장 (검색 및 분석용)
        // 3. 외부 로그 수집 시스템으로 전송 (Datadog, Splunk 등)
    }
}
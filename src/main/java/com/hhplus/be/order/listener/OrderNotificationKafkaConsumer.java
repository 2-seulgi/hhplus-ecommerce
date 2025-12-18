package com.hhplus.be.order.listener;

import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 주문 알림 Kafka Consumer (Presentation 레이어)
 *
 * 역할:
 * - order.confirmed 토픽에서 메시지 수신
 * - 사용자에게 주문 완료 알림 전송
 *
 * 장점:
 * - 알림 실패가 주문을 막지 않음
 * - 데이터 플랫폼 Consumer와 독립적으로 동작
 * - 나중에 알림 서비스로 분리 가능
 *
 * 동일 토픽, 다른 Consumer Group:
 * - order.confirmed 토픽을 OrderDataPlatformKafkaConsumer도 구독
 * - 같은 메시지를 두 Consumer가 독립적으로 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationKafkaConsumer {

    /**
     * order.confirmed 토픽 구독 (알림용)
     *
     * 주의: 다른 Consumer와 다른 groupId 사용
     * - ecommerce-notification-consumer-group
     * - 같은 메시지를 독립적으로 받기 위함
     *
     * @param event 주문 완료 이벤트
     * @param ack 수동 커밋용 Acknowledgment
     */
    @KafkaListener(
            topics = "order.confirmed",
            groupId = "ecommerce-notification-consumer-group",  // 다른 groupId!
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderConfirmedForNotification(OrderConfirmedEvent event, Acknowledgment ack) {
        try {
            log.info("📥 [Notification Consumer] 주문 완료 이벤트 수신 - orderId: {}, userId: {}",
                    event.getOrderId(), event.getUserId());

            // 알림 전송 로직
            sendNotification(event);

            // 수동 커밋
            ack.acknowledge();

            log.info("✅ [Notification Consumer] 알림 전송 완료 - orderId: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("❌ [Notification Consumer] 알림 전송 실패 - orderId: {}, error: {}",
                    event.getOrderId(), e.getMessage(), e);

            // 알림 실패는 치명적이지 않으므로 커밋
            // (필요하면 재시도 로직 추가 가능)
            ack.acknowledge();
        }
    }

    /**
     * 사용자에게 알림 전송
     * TODO: 실제 알림 시스템 연동 (FCM, Email, SMS 등)
     */
    private void sendNotification(OrderConfirmedEvent event) {
        // 알림 메시지 생성
        String message = String.format(
                "🎉 주문이 완료되었습니다!\n" +
                "주문번호: %d\n" +
                "상품수: %d개\n" +
                "감사합니다!",
                event.getOrderId(),
                event.getOrderItems().size()
        );

        log.info("📢 [알림 발송] userId: {}, message: {}", event.getUserId(), message);

        // TODO: 실제 환경에서는 다음 중 하나 선택
        // 1. Firebase Cloud Messaging (FCM) - 모바일 푸시
        // 2. Email 발송 (SendGrid, AWS SES 등)
        // 3. SMS 발송 (Twilio, AWS SNS 등)
        // 4. 앱 내 알림 테이블에 저장
        // 5. WebSocket으로 실시간 전송

        // Mock 지연 (실제 API 호출 시뮬레이션)
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("✅ [알림 발송 완료] userId: {}, orderId: {}", event.getUserId(), event.getOrderId());
    }
}
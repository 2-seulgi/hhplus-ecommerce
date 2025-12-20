package com.hhplus.be.order.listener;

import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;

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
 *
 * 재시도 전략:
 * - Retryable 예외 (일시적 장애): 재시도 → DLT
 * - Non-Retryable 예외 (영구 실패): ACK + 로그
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationKafkaConsumer {

    /**
     * order.confirmed 토픽 구독 (알림용)
     *
     * 주의: 다른 Consumer와 다른 groupId 사용
     * - 같은 토픽을 여러 Consumer가 독립적으로 소비
     * - 데이터 플랫폼 전송과 알림 전송이 서로 영향을 주지 않음
     *
     * 설정:
     * - groupId: application.yml에서 주입 (환경별 설정 가능)
     * - concurrency: 파티션 수에 맞춰 동적 조정 가능
     *
     * @param event 주문 완료 이벤트
     * @param ack 수동 커밋용 Acknowledgment
     */
    @KafkaListener(
            topics = "order.confirmed",
            groupId = "${spring.kafka.consumer.order-notification.group-id}",
            concurrency = "${spring.kafka.consumer.order-notification.concurrency}",
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
            handleException(event, ack, e);
        }
    }

    /**
     * 예외 타입별 처리 전략
     *
     * Retryable (재시도 가능):
     * - 네트워크 타임아웃, 일시적 서버 오류
     * - 예외를 던져서 Kafka 재시도 메커니즘 활용
     * - 최종 실패 시 DLT로 전송
     *
     * Non-Retryable (재시도 불가):
     * - 잘못된 데이터, 존재하지 않는 사용자
     * - ACK + 로그 (메시지 소거)
     */
    private void handleException(OrderConfirmedEvent event, Acknowledgment ack, Exception e) {
        log.error("❌ [Notification Consumer] 알림 전송 실패 - orderId: {}, error: {}",
                event.getOrderId(), e.getMessage(), e);

        // Retryable 예외 판단
        if (isRetryableException(e)) {
            log.warn("⚠️ [Notification Consumer] 재시도 가능한 오류 - 예외 전파");
            // ACK 하지 않고 예외를 던져서 Kafka 재시도
            throw new RuntimeException("Retryable error: " + e.getMessage(), e);
        } else {
            // Non-Retryable: 영구 실패로 간주
            log.error("💀 [Notification Consumer] 영구 실패 - 메시지 소거 - orderId: {}, error: {}",
                    event.getOrderId(), e.getMessage());
            ack.acknowledge();
        }
    }

    /**
     * 재시도 가능한 예외인지 판단
     *
     * Retryable:
     * - SocketTimeoutException: 네트워크 타임아웃
     * - ResourceAccessException: RestTemplate 연결 실패
     * - HttpServerErrorException: 5xx 서버 오류
     *
     * Non-Retryable:
     * - IllegalArgumentException: 잘못된 데이터
     * - NullPointerException: 필수 데이터 누락
     * - 기타 모든 예외 (기본값)
     */
    private boolean isRetryableException(Exception e) {
        // 일시적 네트워크 오류
        if (e instanceof SocketTimeoutException) {
            return true;
        }
        if (e instanceof ResourceAccessException) {
            return true;
        }
        if (e instanceof HttpServerErrorException) {
            return true;
        }

        // 그 외는 Non-Retryable (영구 실패)
        return false;
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

        // Mock: 간혹 실패하는 시뮬레이션 (테스트용)
        // if (Math.random() < 0.1) {
        //     throw new ResourceAccessException("Notification service timeout");
        // }

        log.info("✅ [알림 발송 완료] userId: {}, orderId: {}", event.getUserId(), event.getOrderId());
    }
}

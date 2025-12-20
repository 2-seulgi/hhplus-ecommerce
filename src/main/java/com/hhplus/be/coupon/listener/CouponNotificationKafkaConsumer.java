package com.hhplus.be.coupon.listener;

import com.hhplus.be.coupon.domain.event.CouponIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;

import java.net.SocketTimeoutException;

/**
 * 쿠폰 발급 알림 Kafka Consumer
 *
 * 역할:
 * - coupon.issued 토픽에서 쿠폰 발급 이벤트 소비
 * - 사용자에게 쿠폰 발급 알림 전송
 * - SMS, 푸시 알림, 이메일 등
 *
 * 재시도 전략:
 * - Retryable 예외 (일시적 장애): 재시도 → DLT
 * - Non-Retryable 예외 (영구 실패): ACK + 로그
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponNotificationKafkaConsumer {

    @KafkaListener(
            topics = "coupon.issued",
            groupId = "ecommerce-coupon-notification-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeCouponIssuedForNotification(CouponIssuedEvent event, Acknowledgment ack) {
        try {
            log.info("📥 [Coupon Notification Consumer] 쿠폰 발급 이벤트 수신 - userId: {}, couponId: {}, couponName: {}",
                    event.getUserId(), event.getCouponId(), event.getCouponName());

            // 알림 전송 (현재는 로그만 출력)
            sendNotification(event);

            // 메시지 처리 완료 확인 (수동 커밋)
            ack.acknowledge();

            log.info("✅ [Coupon Notification Consumer] 알림 전송 완료 - userId: {}, couponName: {}",
                    event.getUserId(), event.getCouponName());

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
    private void handleException(CouponIssuedEvent event, Acknowledgment ack, Exception e) {
        log.error("❌ [Coupon Notification Consumer] 알림 전송 실패 - userId: {}, couponId: {}, error: {}",
                event.getUserId(), event.getCouponId(), e.getMessage(), e);

        // Retryable 예외 판단
        if (isRetryableException(e)) {
            log.warn("⚠️ [Coupon Notification Consumer] 재시도 가능한 오류 - 예외 전파");
            // ACK 하지 않고 예외를 던져서 Kafka 재시도
            throw new RuntimeException("Retryable error: " + e.getMessage(), e);
        } else {
            // Non-Retryable: 영구 실패로 간주
            log.error("💀 [Coupon Notification Consumer] 영구 실패 - 메시지 소거 - userId: {}, error: {}",
                    event.getUserId(), e.getMessage());
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
     * 사용자에게 쿠폰 발급 알림 전송
     *
     * TODO: 실제 알림 시스템 연동
     * - SMS: 문자 전송
     * - Push: 모바일 푸시 알림
     * - Email: 이메일 전송
     */
    private void sendNotification(CouponIssuedEvent event) {
        log.info("🔔 쿠폰 발급 알림 - userId: {}, couponName: \"{}\" 쿠폰이 발급되었습니다!",
                event.getUserId(), event.getCouponName());

        // TODO: 실제 알림 서비스 호출
        // smsService.send(event.getUserId(), "쿠폰 발급 알림");
        // pushService.send(event.getUserId(), "쿠폰 발급 알림");

        // Mock: 간혹 실패하는 시뮬레이션 (테스트용)
        // if (Math.random() < 0.1) {
        //     throw new ResourceAccessException("Notification service timeout");
        // }
    }
}
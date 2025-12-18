package com.hhplus.be.coupon.listener;

import com.hhplus.be.coupon.domain.event.CouponIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 발급 알림 Kafka Consumer
 *
 * 역할:
 * - coupon.issued 토픽에서 쿠폰 발급 이벤트 소비
 * - 사용자에게 쿠폰 발급 알림 전송
 * - SMS, 푸시 알림, 이메일 등
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
            log.error("❌ [Coupon Notification Consumer] 알림 전송 실패 - userId: {}, couponId: {}, error: {}",
                    event.getUserId(), event.getCouponId(), e.getMessage(), e);

            // 실패해도 ACK (재시도 방지, 알림은 Best Effort)
            ack.acknowledge();
        }
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
    }
}
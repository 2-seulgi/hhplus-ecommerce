package com.hhplus.be.coupon.infrastructure.producer;

import com.hhplus.be.coupon.domain.event.CouponIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 쿠폰 이벤트 Kafka Producer
 *
 * 역할:
 * - 쿠폰 발급 완료 이벤트를 Kafka로 발행
 * - 비동기 발행으로 성능 최적화
 * - 발행 실패 시 로그 기록 (재시도는 Consumer가 처리)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "coupon.issued";

    /**
     * 쿠폰 발급 이벤트 발행
     *
     * @param event 쿠폰 발급 이벤트
     */
    public void publish(CouponIssuedEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(TOPIC, String.valueOf(event.getUserId()), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("✅ [Kafka Producer] 쿠폰 발급 이벤트 전송 성공 - userId: {}, couponId: {}, offset: {}",
                            event.getUserId(), event.getCouponId(), result.getRecordMetadata().offset());
                } else {
                    log.error("❌ [Kafka Producer] 쿠폰 발급 이벤트 전송 실패 - userId: {}, couponId: {}",
                            event.getUserId(), event.getCouponId(), ex);
                }
            });

        } catch (Exception e) {
            log.error("❌ [Kafka Producer] 쿠폰 발급 이벤트 전송 중 오류 - userId: {}, couponId: {}",
                    event.getUserId(), event.getCouponId(), e);
        }
    }
}
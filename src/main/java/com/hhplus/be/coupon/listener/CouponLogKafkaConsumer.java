package com.hhplus.be.coupon.listener;

import com.hhplus.be.coupon.domain.event.CouponIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 발급 로그/통계 Kafka Consumer
 *
 * 역할:
 * - coupon.issued 토픽에서 쿠폰 발급 이벤트 소비
 * - 쿠폰 발급 이력 로그 저장
 * - 통계 데이터 수집 (발급 수, 사용률 등)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponLogKafkaConsumer {

    @KafkaListener(
            topics = "coupon.issued",
            groupId = "ecommerce-coupon-log-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeCouponIssuedForLog(CouponIssuedEvent event, Acknowledgment ack) {
        try {
            log.info("📥 [Coupon Log Consumer] 쿠폰 발급 이벤트 수신 - userId: {}, couponId: {}, issuedAt: {}",
                    event.getUserId(), event.getCouponId(), event.getIssuedAt());

            // 로그 저장 및 통계 집계
            saveLog(event);

            // 메시지 처리 완료 확인 (수동 커밋)
            ack.acknowledge();

            log.info("✅ [Coupon Log Consumer] 로그 저장 완료 - userId: {}, couponId: {}",
                    event.getUserId(), event.getCouponId());

        } catch (Exception e) {
            log.error("❌ [Coupon Log Consumer] 로그 저장 실패 - userId: {}, couponId: {}, error: {}",
                    event.getUserId(), event.getCouponId(), e.getMessage(), e);

            // 실패해도 ACK (재시도 방지, 로그는 Best Effort)
            ack.acknowledge();
        }
    }

    /**
     * 쿠폰 발급 이력 로그 저장 및 통계 집계
     *
     * TODO: 실제 로그 시스템 연동
     * - ELK Stack: Elasticsearch에 로그 저장
     * - Metrics: Prometheus/Grafana 통계 수집
     * - BigQuery: 데이터 웨어하우스 적재
     */
    private void saveLog(CouponIssuedEvent event) {
        log.info("📊 쿠폰 발급 로그 저장 - " +
                        "userCouponId: {}, userId: {}, couponId: {}, couponName: \"{}\", issuedAt: {}",
                event.getUserCouponId(), event.getUserId(), event.getCouponId(),
                event.getCouponName(), event.getIssuedAt());

        // TODO: 실제 로그/통계 시스템 연동
        // elasticsearchService.save(event);
        // prometheusService.incrementCounter("coupon_issued_total");
    }
}
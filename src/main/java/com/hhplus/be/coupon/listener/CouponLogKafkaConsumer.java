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
 *
 * 멱등성 전략:
 * - 로그/통계는 Best Effort 수집 (중복 허용)
 * - 실패 시에도 ACK 처리 (재시도 하지 않음)
 * - 로그 시스템 특성상 일부 중복/누락 허용 가능
 * - 핵심 비즈니스 로직(쿠폰 발급)은 이미 DB 트랜잭션에서 처리 완료 상태
 * - Consumer 리밸런싱/재시작 시 중복 수신 가능하지만, 로그/통계 특성상 허용 가능
 * - 만약 정확한 통계가 필요하다면:
 *   1. 별도 처리 이력 테이블 + 중복 체크 추가
 *   2. 또는 배치 집계 시 DISTINCT 처리로 중복 제거
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponLogKafkaConsumer {

    /**
     * coupon.issued 토픽 구독 (로그/통계용)
     *
     * 설정:
     * - groupId: application.yml에서 주입 (환경별 설정 가능)
     * - concurrency: 파티션 수에 맞춰 동적 조정 가능
     */
    @KafkaListener(
            topics = "coupon.issued",
            groupId = "${spring.kafka.consumer.coupon-log.group-id}",
            concurrency = "${spring.kafka.consumer.coupon-log.concurrency}",
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
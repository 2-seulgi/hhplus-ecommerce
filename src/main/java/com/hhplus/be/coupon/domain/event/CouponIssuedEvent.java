package com.hhplus.be.coupon.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 쿠폰 발급 완료 이벤트
 *
 * Redis에서 선착순 재고 차감 후 DB 저장 성공 시 발행
 * - Kafka로 발행하여 다양한 Consumer가 처리
 * - 알림, 로그, 통계 등
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CouponIssuedEvent {
    private Long userCouponId;
    private Long userId;
    private Long couponId;
    private String couponName;
    private Instant issuedAt;

    public static CouponIssuedEvent of(Long userCouponId, Long userId, Long couponId, String couponName, Instant issuedAt) {
        return new CouponIssuedEvent(userCouponId, userId, couponId, couponName, issuedAt);
    }
}
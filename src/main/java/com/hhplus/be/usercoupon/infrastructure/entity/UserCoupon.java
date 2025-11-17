package com.hhplus.be.usercoupon.infrastructure.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "user_coupons",
    indexes = {
        // 사용자 쿠폰 조회 최적화: user_id + used + issued_at 복합 인덱스
        @Index(name = "idx_user_coupon_user_used_issued", columnList = "userId, used, issuedAt")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private boolean used;

    @Column
    private Instant usedAt;

    @Column(nullable = false)
    private Instant issuedAt;
}
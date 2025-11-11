package com.hhplus.be.usercoupon.domain;

import com.hhplus.be.common.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "user_coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    private UserCoupon(Long userId, Long couponId, Instant issuedAt) {
        this.userId = userId;
        this.couponId = couponId;
        this.used = false;
        this.usedAt = null;
        this.issuedAt = issuedAt;
    }

    public static UserCoupon create(Long userId, Long couponId, Instant issuedAt) {
        return new UserCoupon(userId, couponId, issuedAt);
    }

    // 쿠폰 사용 처리
    public void use() {
        if (this.used) {
            throw new BusinessException("이미 사용된 쿠폰입니다", "ALREADY_USED");
        }
        this.used = true;
        this.usedAt = Instant.now();
    }
}
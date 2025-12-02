package com.hhplus.be.usercoupon.domain.model;

import com.hhplus.be.common.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon {
    private Long id;
    private Long userId;
    private Long couponId;
    private boolean used;
    private Instant usedAt;
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

    // Mapper용 reconstruct 생성자
    private UserCoupon(Long id, Long userId, Long couponId, boolean used,
                       Instant usedAt, Instant issuedAt) {
        this.id = id;
        this.userId = userId;
        this.couponId = couponId;
        this.used = used;
        this.usedAt = usedAt;
        this.issuedAt = issuedAt;
    }

    public static UserCoupon reconstruct(Long id, Long userId, Long couponId,
                                         boolean used, Instant usedAt, Instant issuedAt) {
        return new UserCoupon(id, userId, couponId, used, usedAt, issuedAt);
    }

    // 쿠폰 사용 처리
    public void use() {
        if (this.used) {
            throw new BusinessException("이미 사용된 쿠폰입니다", "ALREADY_USED");
        }
        this.used = true;
        this.usedAt = Instant.now();
    }

    /**
     * 쿠폰 사용 취소 (보상 트랜잭션용)
     * 결제 실패 시 쿠폰을 다시 사용 가능 상태로 복원
     */
    public void restore() {
        this.used = false;
        this.usedAt = null;
    }
}
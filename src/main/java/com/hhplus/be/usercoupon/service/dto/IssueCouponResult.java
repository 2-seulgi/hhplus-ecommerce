package com.hhplus.be.usercoupon.service.dto;

import com.hhplus.be.coupon.domain.Coupon;
import com.hhplus.be.coupon.domain.DiscountType;
import com.hhplus.be.usercoupon.UserCoupon;

import java.time.Instant;

/**
 * 쿠폰 발급 Result
 * API: POST /users/{userId}/coupons/{couponId}/issue
 */
public record IssueCouponResult(
        Long userCouponId,
        Long userId,
        Long couponId,
        String couponName,
        DiscountType discountType,
        int discountValue,
        Instant useStartAt,
        Instant useEndAt,
        Instant issuedAt
) {
    public static IssueCouponResult from(UserCoupon userCoupon, Coupon coupon) {
        return new IssueCouponResult(
                userCoupon.getId(),
                userCoupon.getUserId(),
                userCoupon.getCouponId(),
                coupon.getName(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getUseStartAt(),
                coupon.getUseEndAt(),
                userCoupon.getIssuedAt()
        );
    }
}
package com.hhplus.be.usercoupon.controller.dto;

import com.hhplus.be.coupon.domain.DiscountType;
import com.hhplus.be.usercoupon.service.dto.IssueCouponResult;

import java.time.Instant;

/**
 * 쿠폰 발급 Response
 * API: POST /users/{userId}/coupons/{couponId}/issue
 */
public record IssueCouponResponse(
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
    public static IssueCouponResponse from(IssueCouponResult result) {
        return new IssueCouponResponse(
                result.userCouponId(),
                result.userId(),
                result.couponId(),
                result.couponName(),
                result.discountType(),
                result.discountValue(),
                result.useStartAt(),
                result.useEndAt(),
                result.issuedAt()
        );
    }
}

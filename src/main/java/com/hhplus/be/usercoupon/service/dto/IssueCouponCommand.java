package com.hhplus.be.usercoupon.service.dto;

/**
 * 쿠폰 발급 Command
 * API: POST /users/{userId}/coupons/{couponId}/issue
 */
public record IssueCouponCommand(
        Long userId,
        Long couponId
) {
}
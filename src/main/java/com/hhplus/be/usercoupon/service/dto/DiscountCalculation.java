package com.hhplus.be.usercoupon.service.dto;

/**
 * 할인 계산 결과
 */
public record DiscountCalculation(
        Long userCouponId,
        Long couponId,
        int discountValue,
        int discountAmount
) {
    public static DiscountCalculation noDiscount() {
        return new DiscountCalculation(null, null, 0, 0);
    }

    public boolean hasDiscount() {
        return userCouponId != null;
    }
}
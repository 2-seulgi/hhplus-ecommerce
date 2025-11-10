package com.hhplus.be.coupon.service.dto;

public record DiscountCalculationResult
(
        Long userCouponId,
        Long couponId,
        int discountValue,
        int discountAmount
){
    public static DiscountCalculationResult noDiscount() {
        return new DiscountCalculationResult(null, null, 0, 0);
    }
    public boolean hasDiscount() {
        return userCouponId != null ;
    }

}

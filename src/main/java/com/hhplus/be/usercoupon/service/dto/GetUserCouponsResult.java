package com.hhplus.be.usercoupon.service.dto;

import com.hhplus.be.coupon.domain.Coupon;
import com.hhplus.be.coupon.domain.DiscountType;
import com.hhplus.be.usercoupon.UserCoupon;

import java.time.Instant;
import java.util.List;

/**
 * 보유 쿠폰 조회 Result
 * API: GET /users/{userId}/coupons?available=false
 */
public record GetUserCouponsResult(
        List<UserCouponInfo> coupons
) {
    public record UserCouponInfo(
            Long userCouponId,
            Long couponId,
            String couponName,
            DiscountType discountType,
            int discountValue,
            boolean used,
            Instant useStartAt,
            Instant useEndAt,
            Instant issuedAt,
            Instant usedAt
    ) {
        public static UserCouponInfo from(UserCoupon userCoupon, Coupon coupon) {
            return new UserCouponInfo(
                    userCoupon.getId(),
                    userCoupon.getCouponId(),
                    coupon.getName(),
                    coupon.getDiscountType(),
                    coupon.getDiscountValue(),
                    userCoupon.isUsed(),
                    coupon.getUseStartAt(),
                    coupon.getUseEndAt(),
                    userCoupon.getIssuedAt(),
                    userCoupon.getUsedAt()
            );
        }
    }
}
package com.hhplus.be.usercoupon.controller.dto;

import com.hhplus.be.coupon.domain.model.DiscountType;
import com.hhplus.be.usercoupon.service.dto.GetUserCouponsResult;

import java.time.Instant;
import java.util.List;

/**
 * 보유 쿠폰 조회 Response
 * API: GET /users/{userId}/coupons?available=false
 */
public record GetUserCouponsResponse(
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
        public static UserCouponInfo from(GetUserCouponsResult.UserCouponInfo info) {
            return new UserCouponInfo(
                    info.userCouponId(),
                    info.couponId(),
                    info.couponName(),
                    info.discountType(),
                    info.discountValue(),
                    info.used(),
                    info.useStartAt(),
                    info.useEndAt(),
                    info.issuedAt(),
                    info.usedAt()
            );
        }
    }

    public static GetUserCouponsResponse from(GetUserCouponsResult result) {
        List<UserCouponInfo> coupons = result.coupons().stream()
                .map(UserCouponInfo::from)
                .toList();
        return new GetUserCouponsResponse(coupons);
    }
}
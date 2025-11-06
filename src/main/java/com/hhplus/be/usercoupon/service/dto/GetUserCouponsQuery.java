package com.hhplus.be.usercoupon.service.dto;

/**
 * 보유 쿠폰 조회 Query
 * API: GET /users/{userId}/coupons?available=false
 */
public record GetUserCouponsQuery(
        Long userId,
        Boolean available  // true면 사용 가능한 쿠폰만, false면 전체
) {
}
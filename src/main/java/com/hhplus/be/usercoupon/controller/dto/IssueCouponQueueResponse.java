package com.hhplus.be.usercoupon.controller.dto;

/**
 * 쿠폰 발급 큐 Response
 *
 * API: POST /users/{userId}/coupons/{couponId}/issue
 *
 * 응답 상태:
 * - 202 Accepted: 발급 처리 중
 * - 400 Bad Request: 선착순 마감
 */
public record IssueCouponQueueResponse(
        boolean success,
        Long position,
        String message,
        String resultUrl
) {
    /**
     * 발급 요청 접수됨 (202 Accepted)
     *
     * @param position 대기 순번 (1부터 시작)
     * @param message 안내 메시지
     */
    public static IssueCouponQueueResponse accepted(Long position, String message, String resultUrl) {
        return new IssueCouponQueueResponse(true, position, message, resultUrl);
    }

    /**
     * 선착순 마감 (400 Bad Request)
     *
     * @param message 안내 메시지
     */
    public static IssueCouponQueueResponse failedSoldOut(String message) {
        return new IssueCouponQueueResponse(false, null, message, null);
    }
}
package com.hhplus.be.order.service.dto;

import com.hhplus.be.order.domain.OrderStatus;

import java.time.Instant;

/**
 * 환불 처리 Result
 * API: POST /users/{userId}/orders/{orderId}/refund
 */
public record RefundResult(
        Long orderId,
        Long userId,
        OrderStatus status,
        int refundedAmount,     // 환불된 금액
        int currentBalance,     // 환불 후 현재 포인트
        Instant refundedAt      // 환불 시각
) {
}
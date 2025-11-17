package com.hhplus.be.order.controller.dto;

import com.hhplus.be.order.domain.model.OrderStatus;
import com.hhplus.be.order.service.dto.RefundResult;

import java.time.Instant;

/**
 * 환불 응답 DTO
 * API: POST /users/{userId}/orders/{orderId}/refund
 */
public record RefundResponse(
        Long orderId,
        Long userId,
        OrderStatus status,
        int refundedAmount,
        int currentBalance,
        Instant refundedAt
) {
    public static RefundResponse from(RefundResult result) {
        return new RefundResponse(
                result.orderId(),
                result.userId(),
                result.status(),
                result.refundedAmount(),
                result.currentBalance(),
                result.refundedAt()
        );
    }
}
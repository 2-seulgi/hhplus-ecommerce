package com.hhplus.be.order.controller.dto;

import com.hhplus.be.order.domain.model.OrderStatus;
import com.hhplus.be.order.service.dto.PaymentResult;

import java.time.Instant;

/**
 * 결제 응답 DTO
 * API: POST /users/{userId}/orders/{orderId}/payment
 */
public record PaymentResponse(
        Long orderId,
        Long userId,
        OrderStatus status,
        int totalAmount,
        int discountAmount,
        int finalAmount,
        int remainingBalance,
        Instant paidAt
) {
    public static PaymentResponse from(PaymentResult result) {
        return new PaymentResponse(
                result.orderId(),
                result.userId(),
                result.status(),
                result.totalAmount(),
                result.discountAmount(),
                result.finalAmount(),
                result.remainingBalance(),
                result.paidAt()
        );
    }
}
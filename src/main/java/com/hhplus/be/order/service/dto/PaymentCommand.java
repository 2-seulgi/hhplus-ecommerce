package com.hhplus.be.order.service.dto;

/**
 * 결제 처리 Command
 * API: POST /users/{userId}/orders/{orderId}/payment
 */
public record PaymentCommand(
        Long userId,
        Long orderId,
        String couponCode  // 선택적 (null 가능)
) {
}
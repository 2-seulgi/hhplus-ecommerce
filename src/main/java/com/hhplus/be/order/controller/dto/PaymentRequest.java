package com.hhplus.be.order.controller.dto;

/**
 * 결제 요청 DTO
 * API: POST /users/{userId}/orders/{orderId}/payment
 */
public record PaymentRequest(
        String couponCode  // 선택적 (null 가능)
) {
}
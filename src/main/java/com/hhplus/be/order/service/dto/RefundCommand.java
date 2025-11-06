package com.hhplus.be.order.service.dto;

/**
 * 환불 처리 Command
 * API: POST /users/{userId}/orders/{orderId}/refund
 */
public record RefundCommand(
        Long userId,
        Long orderId
) {
}
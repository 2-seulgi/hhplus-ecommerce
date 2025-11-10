package com.hhplus.be.order.service.dto;

import com.hhplus.be.order.domain.Order;
import com.hhplus.be.order.domain.OrderStatus;
import com.hhplus.be.user.domain.User;

import java.time.Instant;

/**
 * 결제 처리 Result
 * API: POST /users/{userId}/orders/{orderId}/payment
 */
public record PaymentResult(
        Long orderId,
        Long userId,
        OrderStatus status,
        int totalAmount,        // 원래 주문 금액
        int discountAmount,     // 할인 금액 (쿠폰 등)
        int finalAmount,        // 실제 결제 금액 (totalAmount - discountAmount)
        int remainingBalance,   // 결제 후 남은 포인트
        Instant paidAt          // 결제 시각
) {
    public static PaymentResult from(Order order, User user, int discountAmount) {
        return new PaymentResult(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                discountAmount,
                order.getFinalAmount(),    // 결제 확정 시점에 설정된 finalAmount
                user.getBalance(),         // 결제 후 잔액
                order.getPaidAt()          // 결제 완료 시각
        );
    }
}
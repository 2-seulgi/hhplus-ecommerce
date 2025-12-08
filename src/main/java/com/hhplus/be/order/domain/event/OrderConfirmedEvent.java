package com.hhplus.be.order.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 주문 완료 이벤트
 *
 * 발행 시점: 결제 완료 후 주문이 CONFIRMED 상태로 변경될 때
 * 용도: 실시간 상품 랭킹 업데이트 (비동기)
 *
 * 처리 전략:
 * - 주문 트랜잭션: 동기 (재고, 포인트, DB 저장)
 * - 랭킹 업데이트: 비동기 (@Async + @TransactionalEventListener)
 */
@Getter
@RequiredArgsConstructor
public class OrderConfirmedEvent {
    private final Long orderId;
    private final Long userId;
    private final List<OrderItemInfo> orderItems;
    private final Instant confirmedAt;

    /**
     * 주문 항목 정보 (이벤트용)
     * 도메인 모델과 분리하여 이벤트 전용 DTO 사용
     */
    @Getter
    @RequiredArgsConstructor
    public static class OrderItemInfo {
        private final Long productId;
        private final String productName;
        private final int quantity;
    }
}
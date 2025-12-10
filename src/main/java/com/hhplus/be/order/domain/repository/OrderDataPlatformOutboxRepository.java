package com.hhplus.be.order.domain.repository;

import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.model.OutboxStatus;

import java.util.List;
import java.util.Optional;

public interface OrderDataPlatformOutboxRepository {
    /**
     * Outbox 레코드 저장
     */
    OrderDataPlatformOutbox save(OrderDataPlatformOutbox outbox);

    /**
     * ID로 조회
     */
    Optional<OrderDataPlatformOutbox> findById(Long id);

    /**
     * 주문 ID로 조회 (특정 주문의 전송 이력)
     */
    Optional<OrderDataPlatformOutbox> findByOrderId(Long orderId);

    /**
     * 상태별 조회 (실패한 건들 찾기용)
     */
    List<OrderDataPlatformOutbox> findByStatus(OutboxStatus status);

    /**
     * 테스트용 전체 삭제
     */
    void deleteAll();
}
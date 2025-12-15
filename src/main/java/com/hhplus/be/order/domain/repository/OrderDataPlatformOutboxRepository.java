package com.hhplus.be.order.domain.repository;

import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.model.OutboxStatus;

import java.time.Instant;
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
     * 특정 상태의 Outbox 조회 (재시도 대상)
     *
     * @param status 조회할 상태
     * @param maxRetryCount 최대 재시도 횟수
     * @param before 생성 시각 기준 (이 시각 이전 레코드만)
     * @return Outbox 목록
     */
    List<OrderDataPlatformOutbox> findByStatusAndRetryCountLessThanAndCreatedAtBefore(
            OutboxStatus status,
            int maxRetryCount,
            Instant before
    );

    /**
     * 테스트용 전체 삭제
     */
    void deleteAll();
}
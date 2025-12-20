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
     * PENDING 상태의 Outbox 조회 (Kafka 발행 대상, 최대 100개)
     *
     * @param status 조회할 상태
     * @return Outbox 목록 (생성 시각 오름차순 정렬, 최대 100개)
     */
    List<OrderDataPlatformOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    /**
     * Stuck PUBLISHING 상태 조회 (timeout 기반)
     *
     * 목적:
     * - PUBLISHING 상태로 일정 시간 이상 머물러 있는 레코드 찾기
     * - 프로세스가 전송 중 죽어서 상태 전이가 멈춘 경우 감지
     *
     * 사용:
     * - Stuck PUBLISHING Scheduler가 주기적으로 호출
     * - timeout 이상 PUBLISHING 상태면 FAILED 처리
     *
     * @param status PUBLISHING 상태
     * @param updatedBefore 이 시각 이전에 업데이트된 레코드 (timeout 판단 기준)
     * @return Stuck 상태의 Outbox 목록
     */
    List<OrderDataPlatformOutbox> findByStatusAndUpdatedAtBefore(
            OutboxStatus status,
            Instant updatedBefore
    );

    /**
     * 테스트용 전체 삭제
     */
    void deleteAll();
}
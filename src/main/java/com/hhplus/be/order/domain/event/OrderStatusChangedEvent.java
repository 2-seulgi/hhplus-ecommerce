package com.hhplus.be.order.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

/**
 * 주문 상태 변경 이벤트
 *
 * 발행 시점: 주문 상태가 변경될 때마다
 * 용도: 로그 저장, 모니터링, 감사(Audit)
 *
 * 처리 전략:
 * - 주문 트랜잭션: 동기
 * - 로그 저장: 비동기 (Kafka → LogConsumer)
 *
 * 장점:
 * - 로그 저장 실패가 주문을 막지 않음
 * - 모든 상태 변경 이력 추적 가능
 * - 나중에 감사 서비스로 분리 가능
 */
@Getter
@RequiredArgsConstructor
public class OrderStatusChangedEvent {
    private final Long orderId;
    private final Long userId;
    private final String previousStatus;  // 이전 상태 (최초 생성 시 null)
    private final String newStatus;       // 새 상태
    private final String reason;          // 변경 이유 (선택적)
    private final Instant changedAt;
}
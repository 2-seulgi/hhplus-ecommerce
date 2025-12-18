package com.hhplus.be.order.domain.model;

/**
 * 데이터 플랫폼 전송 상태
 */
public enum OutboxStatus {
    PENDING,    // 전송 대기 (Outbox에 저장됨)
    PUBLISHED,  // Kafka에 발행 완료 (Consumer 처리 대기 중)
    SUCCESS,    // Consumer가 처리 완료
    FAILED      // 최종 실패 (재시도 횟수 초과)
}
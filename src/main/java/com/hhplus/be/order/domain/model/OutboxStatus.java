package com.hhplus.be.order.domain.model;

/**
 * 데이터 플랫폼 전송 상태
 *
 * 상태 전이:
 * PENDING → PUBLISHING → PUBLISHED → SUCCESS
 *         ↓           ↓
 *         FAILED ← FAILED
 *
 * Stuck 방지:
 * - PUBLISHING 상태가 일정 시간 이상 유지되면 FAILED로 전환
 * - updated_at 기준으로 timeout 판단 (기본 5분)
 */
public enum OutboxStatus {
    PENDING,     // 전송 대기 (Outbox에 저장됨)
    PUBLISHING,  // Kafka 전송 중 (프로세스 죽으면 stuck 가능 → timeout 처리 필요)
    PUBLISHED,   // Kafka에 발행 완료 (Consumer 처리 대기 중)
    SUCCESS,     // Consumer가 처리 완료
    FAILED       // 최종 실패 (재시도 횟수 초과 또는 timeout)
}
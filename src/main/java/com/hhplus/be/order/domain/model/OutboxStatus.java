package com.hhplus.be.order.domain.model;

/**
 * 데이터 플랫폼 전송 상태
 */
public enum OutboxStatus {
    PENDING,   // 전송 대기
    SUCCESS,   // 전송 성공
    FAILED     // 전송 실패 (재시도 횟수 초과)
}
package com.hhplus.be.point.service.dto;

/**
 * 포인트 잔액 조회 Result DTO
 * Application Layer에서 사용
 */
public record PointBalanceResult(
        Long userId,
        int balance
) {
}
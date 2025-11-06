package com.hhplus.be.point.controller.dto;

import com.hhplus.be.point.service.dto.PointBalanceResult;

/**
 * 포인트 잔액 조회 Response DTO
 * Presentation Layer에서 사용
 */
public record PointBalanceResponse(
        Long userId,
        int balance
) {
    public static PointBalanceResponse from(PointBalanceResult result) {
        return new PointBalanceResponse(
                result.userId(),
                result.balance()
        );
    }
}
package com.hhplus.be.point.controller.dto;

import com.hhplus.be.point.service.dto.PointHistoryResult;

/**
 * 포인트 내역 단건 Response DTO
 * Presentation Layer에서 사용
 */
public record PointHistoryResponse(
        Long pointId,
        String pointType,
        int amount,
        int balance,
        String createdAt
) {
    public static PointHistoryResponse from(PointHistoryResult result) {
        return new PointHistoryResponse(
                result.pointId(),
                result.pointType().name(),
                result.amount(),
                result.balanceAfter(),
                result.createdAt().toString()  // Instant -> ISO-8601 string
        );
    }
}
package com.hhplus.be.point.controller.dto;

import com.hhplus.be.point.service.dto.PointChargeResult;
import com.hhplus.be.point.domain.PointType;

/**
 * 포인트 충전 Response DTO
 * Presentation Layer에서 사용
 */
public record PointChargeResponse(
        Long pointId,
        Long userId,
        String pointType,
        int amount,
        int balance,
        String chargedAt
) {
    public static PointChargeResponse from(PointChargeResult result) {
        return new PointChargeResponse(
                result.pointId(),
                result.userId(),
                result.pointType().name(),
                result.amount(),
                result.balanceAfter(),
                result.createdAt().toString()  // Instant -> ISO-8601 string
        );
    }
}
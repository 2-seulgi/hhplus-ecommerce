package com.hhplus.be.point.controller.dto;

import com.hhplus.be.point.service.dto.PointChargeResult;
import com.hhplus.be.point.domain.PointType;

import java.time.format.DateTimeFormatter;

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
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    public static PointChargeResponse from(PointChargeResult result) {
        return new PointChargeResponse(
                result.getPointId(),
                result.getUserId(),
                result.getPointType().name(),
                result.getAmount(),
                result.getBalanceAfter(),
                result.getCreatedAt().format(FORMATTER)
        );
    }
}
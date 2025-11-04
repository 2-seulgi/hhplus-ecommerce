package com.hhplus.be.point.controller.dto;

import com.hhplus.be.point.service.dto.PointHistoryResult;

import java.time.format.DateTimeFormatter;

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
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    public static PointHistoryResponse from(PointHistoryResult result) {
        return new PointHistoryResponse(
                result.getPointId(),
                result.getPointType().name(),
                result.getAmount(),
                result.getBalanceAfter(),
                result.getCreatedAt().format(FORMATTER)
        );
    }
}
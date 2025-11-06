package com.hhplus.be.point.service.dto;

import com.hhplus.be.point.domain.Point;
import com.hhplus.be.point.domain.PointType;

import java.time.LocalDateTime;

/**
 * 포인트 내역 단건 Result DTO
 * Application Layer에서 사용
 */
public record PointHistoryResult(
        Long pointId,
        Long userId,
        PointType pointType,
        int amount,
        int balanceAfter,
        LocalDateTime createdAt
) {
    public static PointHistoryResult from(Point point) {
        return new PointHistoryResult(
                point.getId(),
                point.getUserId(),
                point.getPointType(),
                point.getAmount(),
                point.getBalanceAfter(),
                point.getCreatedAt()
        );
    }
}
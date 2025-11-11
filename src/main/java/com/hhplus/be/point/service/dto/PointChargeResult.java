package com.hhplus.be.point.service.dto;

import com.hhplus.be.point.domain.model.Point;
import com.hhplus.be.point.domain.model.PointType;

import java.time.Instant;

/**
 * 포인트 충전 UseCase Result DTO
 * Application Layer에서 사용
 */
public record PointChargeResult(
        Long pointId,
        Long userId,
        PointType pointType,
        int amount,
        int balanceAfter,
        Instant createdAt
) {
    public static PointChargeResult from(Point point) {
        return new PointChargeResult(
                point.getId(),
                point.getUserId(),
                point.getPointType(),
                point.getAmount(),
                point.getBalanceAfter(),
                point.getCreatedAt()
        );
    }
}
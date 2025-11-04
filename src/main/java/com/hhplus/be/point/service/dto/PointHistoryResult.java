package com.hhplus.be.point.service.dto;

import com.hhplus.be.point.domain.Point;
import com.hhplus.be.point.domain.PointType;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 포인트 내역 단건 Result DTO
 * Application Layer에서 사용
 */
@Getter
public class PointHistoryResult {
    private final Long pointId;
    private final Long userId;
    private final PointType pointType;
    private final int amount;
    private final int balanceAfter;
    private final LocalDateTime createdAt;

    public PointHistoryResult(Point point) {
        this.pointId = point.getId();
        this.userId = point.getUserId();
        this.pointType = point.getPointType();
        this.amount = point.getAmount();
        this.balanceAfter = point.getBalanceAfter();
        this.createdAt = point.getCreatedAt();
    }

}
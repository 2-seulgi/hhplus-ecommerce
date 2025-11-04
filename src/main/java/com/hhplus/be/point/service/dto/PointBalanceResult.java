package com.hhplus.be.point.service.dto;

import lombok.Getter;

/**
 * 포인트 잔액 조회 Result DTO
 * Application Layer에서 사용
 */
@Getter
public class PointBalanceResult {
    private final Long userId;
    private final int balance;

    public PointBalanceResult(Long userId, int balance) {
        this.userId = userId;
        this.balance = balance;
    }

}
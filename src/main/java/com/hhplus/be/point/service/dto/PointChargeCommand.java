package com.hhplus.be.point.service.dto;

/**
 * 포인트 충전 UseCase Command DTO
 * Application Layer에서 사용
 */
public record PointChargeCommand(Long userId, int amount) {

}
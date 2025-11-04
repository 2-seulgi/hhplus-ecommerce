package com.hhplus.be.point.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 포인트 충전 Request DTO
 * Presentation Layer에서 사용
 */
public record PointChargeRequest(
        @NotNull(message = "충전 금액은 필수입니다")
        @Min(value = 1000, message = "최소 충전 금액은 1000원입니다")
        @Max(value = 1000000, message = "최대 충전 금액은 1,000,000원입니다")
        Integer amount
) {
}

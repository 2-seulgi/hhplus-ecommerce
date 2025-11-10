package com.hhplus.be.coupon.service.dto;

import java.time.Instant;

public record ValidateDiscountCommand(
        Long userId,
        String couponCode,
        int orderAmount
) {}
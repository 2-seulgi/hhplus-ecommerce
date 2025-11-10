package com.hhplus.be.order.service.dto;

public record OrderDetailQuery(
        Long userId,
        Long orderId
) {
}
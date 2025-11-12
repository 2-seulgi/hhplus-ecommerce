package com.hhplus.be.order.controller.dto;

import com.hhplus.be.order.domain.model.OrderStatus;
import com.hhplus.be.order.service.dto.OrderDetailResult;

import java.time.Instant;
import java.util.List;

public record OrderDetailResponse(
        Long orderId,
        Long userId,
        OrderStatus status,
        int totalAmount,
        Instant createdAt,
        Instant expiresAt,
        List<Item> items
) {
    public static OrderDetailResponse from(OrderDetailResult result) {
        List<Item> items = result.items().stream()
                .map(i -> new Item(i.productId(), i.productName(), i.unitPrice(), i.quantity()))
                .toList();
        return new OrderDetailResponse(
                result.orderId(),
                result.userId(),
                result.status(),
                result.totalAmount(),
                result.createdAt(),
                result.expiresAt(),
                items
        );
    }

    public record Item(Long productId, String productName, int unitPrice, int quantity) {}
}
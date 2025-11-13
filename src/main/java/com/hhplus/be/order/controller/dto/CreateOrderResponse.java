package com.hhplus.be.order.controller.dto;

import com.hhplus.be.order.domain.model.OrderStatus;
import com.hhplus.be.order.service.dto.CreateOrderResult;

import java.time.Instant;
import java.util.List;

public record CreateOrderResponse(
        Long orderId,
        Long userId,
        OrderStatus status,
        int totalAmount,
        Instant expiresAt,
        List<Item> items
) {
    public static CreateOrderResponse from(CreateOrderResult result) {
        List<Item> items = result.items().stream()
                .map(i -> new Item(i.productId(), i.productName(), i.unitPrice(), i.quantity()))
                .toList();
        return new CreateOrderResponse(
                result.orderId(),
                result.userId(),
                result.status(),
                result.totalAmount(),
                result.expiresAt(),
                items
        );
    }

    public record Item(Long productId, String productName, int unitPrice, int quantity) {}
}
package com.hhplus.be.order.controller.dto;

import com.hhplus.be.order.domain.model.OrderStatus;
import com.hhplus.be.order.service.dto.OrderListResult;

import java.time.Instant;
import java.util.List;

public record OrderListResponse(
        List<OrderSummary> orders
) {
    public static OrderListResponse from(OrderListResult result) {
        List<OrderSummary> orders = result.orders().stream()
                .map(o -> new OrderSummary(
                        o.orderId(),
                        o.userId(),
                        o.status(),
                        o.totalAmount(),
                        o.createdAt(),
                        o.items().stream()
                                .map(i -> new Item(i.productId(), i.productName(), i.unitPrice(), i.quantity()))
                                .toList()
                ))
                .toList();
        return new OrderListResponse(orders);
    }

    public record OrderSummary(
            Long orderId,
            Long userId,
            OrderStatus status,
            int totalAmount,
            Instant createdAt,
            List<Item> items
    ) {}

    public record Item(Long productId, String productName, int unitPrice, int quantity) {}
}
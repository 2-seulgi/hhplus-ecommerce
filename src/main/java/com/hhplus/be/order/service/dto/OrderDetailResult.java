package com.hhplus.be.order.service.dto;

import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.domain.model.OrderStatus;
import com.hhplus.be.orderitem.domain.model.OrderItem;

import java.time.Instant;
import java.util.List;

public record OrderDetailResult(
        Long orderId,
        Long userId,
        OrderStatus status,
        int totalAmount,
        Instant createdAt,
        Instant expiresAt,
        List<Item> items
) {
    public static OrderDetailResult from(Order order, List<OrderItem> orderItems) {
        List<Item> items = orderItems.stream()
                .map(oi -> new Item(
                        oi.getProductId(),
                        oi.getProductName(),
                        oi.getUnitPrice(),
                        oi.getQuantity()
                ))
                .toList();
        return new OrderDetailResult(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getExpiresAt(),
                items
        );
    }

    public record Item(Long productId, String productName, int unitPrice, int quantity) {}
}
package com.hhplus.be.order.service.dto;

import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.domain.model.OrderStatus;
import com.hhplus.be.orderitem.domain.OrderItem;

import java.time.Instant;
import java.util.List;

public record CreateOrderResult(
        Long orderId,
        Long userId,
        OrderStatus status,
        int totalAmount,
        Instant expiresAt,
        List<Item> items
) {
    public static CreateOrderResult from(Order order, List<OrderItem> orderItems) {
        List<Item> resultItems = orderItems.stream()
                .map(oi -> new Item(
                        oi.getProductId(),
                        oi.getProductName(),
                        oi.getUnitPrice(),
                        oi.getQuantity()
                )).toList();
        return new CreateOrderResult(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getExpiresAt(),
                resultItems
        );
    }
    public record Item(Long productId, String productName, int unitPrice, int quantity) {}
}
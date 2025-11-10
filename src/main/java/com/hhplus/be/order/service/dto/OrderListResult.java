package com.hhplus.be.order.service.dto;

import com.hhplus.be.order.domain.Order;
import com.hhplus.be.order.domain.OrderStatus;
import com.hhplus.be.orderitem.domain.OrderItem;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record OrderListResult(
        List<OrderSummary> orders
) {
    public static OrderListResult from(List<Order> orders, Map<Long, List<OrderItem>> itemsByOrderId) {
        List<OrderSummary> summaries = orders.stream()
                .map(order -> {
                    List<OrderItem> items = itemsByOrderId.getOrDefault(order.getId(), List.of());
                    return OrderSummary.from(order, items);
                })
                .toList();
        return new OrderListResult(summaries);
    }

    public record OrderSummary(
            Long orderId,
            Long userId,
            OrderStatus status,
            int totalAmount,
            Instant createdAt,
            List<Item> items
    ) {
        public static OrderSummary from(Order order, List<OrderItem> orderItems) {
            List<Item> items = orderItems.stream()
                    .map(oi -> new Item(
                            oi.getProductId(),
                            oi.getProductName(),
                            oi.getUnitPrice(),
                            oi.getQuantity()
                    ))
                    .toList();
            return new OrderSummary(
                    order.getId(),
                    order.getUserId(),
                    order.getStatus(),
                    order.getTotalAmount(),
                    order.getCreatedAt(),
                    items
            );
        }
    }

    public record Item(Long productId, String productName, int unitPrice, int quantity) {}
}
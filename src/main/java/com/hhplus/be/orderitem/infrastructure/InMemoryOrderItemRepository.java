package com.hhplus.be.orderitem.infrastructure;

import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.domain.model.OrderStatus;
import com.hhplus.be.order.infrastructure.OrderRepository;
import com.hhplus.be.orderitem.domain.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class InMemoryOrderItemRepository implements OrderItemRepository {
    private final Map<Long, OrderItem> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final OrderRepository orderRepository;

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        for (OrderItem item : orderItems) {
            if (item.getId() == null) {
                item.assignId(idGenerator.getAndIncrement());
            }
            store.put(item.getId(), item);
        }
        return orderItems;
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return store.values().stream()
                .filter(item -> item.getOrderId().equals(orderId))
                .toList();
    }

    @Override
    public List<OrderItem> findByOrderIdIn(List<Long> orderIds) {
        return store.values().stream()
                .filter(item -> orderIds.contains(item.getOrderId()))
                .toList();
    }

    /**
     * 최근 N일간 CONFIRMED 주문의 상품별 판매량 집계
     *
     * 실무에서는 SQL로 한방에:
     * SELECT oi.product_id, SUM(oi.quantity) as sales_count
     * FROM order_items oi
     * JOIN orders o ON oi.order_id = o.order_id
     * WHERE o.status = 'CONFIRMED'
     *   AND o.created_at >= :since
     * GROUP BY oi.product_id
     */
    @Override
    public Map<Long, Integer> countSalesByProductSince(Instant since) {
        // 1. 모든 주문 조회
        List<Order> confirmedOrders = orderRepository.findAll().stream()
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED)
                .filter(order -> order.getCreatedAt().isAfter(since))
                .toList();

        List<Long> confirmedOrderIds = confirmedOrders.stream()
                .map(Order::getId)
                .toList();

        // 2. CONFIRMED 주문의 OrderItem들만 조회
        List<OrderItem> items = store.values().stream()
                .filter(item -> confirmedOrderIds.contains(item.getOrderId()))
                .toList();

        // 3. 상품별 판매량 집계
        return items.stream()
                .collect(Collectors.groupingBy(
                        OrderItem::getProductId,
                        Collectors.summingInt(OrderItem::getQuantity)
                ));
    }
}

package com.hhplus.be.order.infrastructure;

import com.hhplus.be.order.domain.model.Order;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryOrderRepository implements OrderRepository {
    private final Map<Long, Order> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Order save(Order order) {
        Long id = idGenerator.getAndIncrement();
        order.assignId(id);
        store.put(id, order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        return Optional.ofNullable(store.get(orderId));
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return store.values().stream()
                .filter(order -> order.getUserId().equals(userId))
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
                .toList();
    }

    @Override
    public List<Order> findAll() {
        return store.values().stream().toList();
    }
}

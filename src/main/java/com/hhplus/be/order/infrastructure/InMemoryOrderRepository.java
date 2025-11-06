package com.hhplus.be.order.infrastructure;

import com.hhplus.be.order.domain.Order;
import org.springframework.stereotype.Repository;

import java.util.Map;
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
}

package com.hhplus.be.orderitem.infrastructure;

import com.hhplus.be.orderitem.domain.OrderItem;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryOrderItemRepository implements OrderItemRepository {
    private final Map<Long, OrderItem> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

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
}

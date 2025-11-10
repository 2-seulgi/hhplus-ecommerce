package com.hhplus.be.orderdiscount.infrastructure;

import com.hhplus.be.orderdiscount.domain.OrderDiscount;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryOrderDiscountRepository implements OrderDiscountRepository {

    private final Map<Long, OrderDiscount> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public OrderDiscount save(OrderDiscount orderDiscount) {
        if (orderDiscount.getId() == null) {
            assignId(orderDiscount, idGenerator.getAndIncrement());
        }
        store.put(orderDiscount.getId(), orderDiscount);
        return orderDiscount;
    }

    @Override
    public List<OrderDiscount> findByOrderId(Long orderId) {
        return store.values().stream()
                .filter(od -> od.getOrderId().equals(orderId))
                .toList();
    }

    // 테스트용 초기화
    public void clear() {
        store.clear();
        idGenerator.set(1);
    }

    // ID 할당을 위한 리플렉션 헬퍼
    private void assignId(OrderDiscount orderDiscount, Long id) {
        try {
            var field = OrderDiscount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(orderDiscount, id);
        } catch (Exception e) {
            throw new RuntimeException("ID 할당 실패", e);
        }
    }
}
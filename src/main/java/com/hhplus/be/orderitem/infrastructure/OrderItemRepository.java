package com.hhplus.be.orderitem.infrastructure;

import com.hhplus.be.orderitem.domain.OrderItem;

import java.util.List;

public interface OrderItemRepository {
    List<OrderItem> saveAll(List<OrderItem> orderItems);
}

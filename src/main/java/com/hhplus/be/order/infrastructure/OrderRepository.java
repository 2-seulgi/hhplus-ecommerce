package com.hhplus.be.order.infrastructure;

import com.hhplus.be.order.domain.Order;

public interface OrderRepository {
    // 1. 저장 : 주문 내역 저장
    Order save(Order order);

    //

}

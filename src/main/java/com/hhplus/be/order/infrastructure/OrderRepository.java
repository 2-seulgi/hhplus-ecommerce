package com.hhplus.be.order.infrastructure;

import com.hhplus.be.order.domain.model.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    // 1. 저장 : 주문 내역 저장
    Order save(Order order);

    // 2. 주문 단건 조회
    Optional<Order> findById(Long orderId);

    // 3. 사용자별 주문 목록 조회
    List<Order> findByUserId(Long userId);

    // 4. 전체 주문 조회 (판매량 집계용)
    List<Order> findAll();
}

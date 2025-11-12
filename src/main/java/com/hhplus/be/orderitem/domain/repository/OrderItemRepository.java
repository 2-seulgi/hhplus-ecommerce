package com.hhplus.be.orderitem.domain.repository;

import com.hhplus.be.orderitem.domain.model.OrderItem;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface OrderItemRepository {
    List<OrderItem> saveAll(List<OrderItem> orderItems);

    // 특정 주문의 항목 조회
    List<OrderItem> findByOrderId(Long orderId);

    // 여러 주문의 항목 조회 (배치 조회)
    List<OrderItem> findByOrderIdIn(List<Long> orderIds);

    /**
     * 최근 N일간 CONFIRMED 주문의 상품별 판매량 집계
     * API: GET /products/top?period=3d&limit=5
     *
     * @param days 기간 (예: 3일)
     * @param since 시작 시점 (예: now - 3일)
     * @return Map<상품ID, 판매수량>
     */
    Map<Long, Integer> countSalesByProductSince(Instant since);
}

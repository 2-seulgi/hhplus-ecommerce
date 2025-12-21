package com.hhplus.be.product.service;

import com.hhplus.be.orderitem.domain.model.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 재고 관리 서비스
 * <p>
 * 여러 상품의 재고를 처리하고, 데드락 방지를 위한 정렬 로직 담당
 * 실제 분산락 적용은 ProductStockLockService에 위임
 */
@Service
@RequiredArgsConstructor
public class ProductStockService {
    private final ProductStockLockService productStockLockService;

    /**
     * 여러 상품의 재고 차감 (데드락 방지 + 분산락 적용)
     * <p>
     * 데드락 방지: productId 정렬 후 순차 처리
     * ProductStockLockService를 통해 프록시 경로로 호출하여 @DistributedLock 보장
     *
     * @param orderItems 주문 항목 리스트
     */
    public void decreaseStocksWithLock(List<OrderItem> orderItems) {
        // 0. 빈 리스트 처리
        if (orderItems == null || orderItems.isEmpty()) {
            return;
        }
        // 1. productId 오름차순 정렬 (데드락 방지)
        List<OrderItem> sortedItems = orderItems.stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .toList();

        // 2. 정렬된 순서로 하나씩 분산락 획득 및 처리
        // ProductStockLockService를 통해 프록시 경로로 호출하여 @DistributedLock 적용 보장
        for (OrderItem item : sortedItems) {
            productStockLockService.decreaseStockWithLock(item.getProductId(), item.getQuantity());
        }
    }

    /**
     * 여러 상품의 재고 증가 (데드락 방지 + 분산락 적용)
     * <p>
     * 데드락 방지: productId 정렬 후 순차 처리
     * ProductStockLockService를 통해 프록시 경로로 호출하여 @DistributedLock 보장
     *
     * @param orderItems 주문 항목 리스트
     */
    public void increaseStocksWithLock(List<OrderItem> orderItems) {
        // 0. 빈 리스트 처리
        if (orderItems == null || orderItems.isEmpty()) {
            return;
        }
        // 1. productId 오름차순 정렬 (데드락 방지)
        List<OrderItem> sortedItems = orderItems.stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .toList();

        // 2. 정렬된 순서로 하나씩 분산락 획득 및 처리
        // ProductStockLockService를 통해 프록시 경로로 호출하여 @DistributedLock 적용 보장
        for (OrderItem item : sortedItems) {
            productStockLockService.increaseStockWithLock(item.getProductId(), item.getQuantity());
        }
    }
}

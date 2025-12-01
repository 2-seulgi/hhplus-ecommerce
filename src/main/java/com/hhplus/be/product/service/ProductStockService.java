package com.hhplus.be.product.service;

import com.hhplus.be.common.annotation.DistributedLock;
import com.hhplus.be.orderitem.domain.model.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductStockService {
    private final ProductService productService;

    /**
     * ✅ 외부에서 호출하는 메서드 (List 받음)
     * 데드락 방지: productId 정렬 후 순차 처리
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
        for (OrderItem item : sortedItems) {
            decreaseStockWithLock(item.getProductId(), item.getQuantity());
        }
    }
    /**
     * 분산락만 담당 - 차감(외부 호출 금지)
     */
    @DistributedLock(key = "'product:stock:' + #productId", waitTime = 3, leaseTime = 5)
    private void decreaseStockWithLock(Long productId, int quantity) {
        productService.decreaseStock(productId, quantity);
    }

    /**
     * 재고 증가 처리 (주문 취소 등)
     * @param orderItems
     */
    public void increaseStocksWithLock(List<OrderItem> orderItems) {
        List<OrderItem> sortedItems = orderItems.stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .toList();

        for (OrderItem item : sortedItems) {
            increaseStockWithLock(item.getProductId(), item.getQuantity());
        }
    }

    @DistributedLock(key = "'product:stock:' + #productId", waitTime = 3, leaseTime = 5)
    private void increaseStockWithLock(Long productId, int quantity) {
        productService.increaseStock(productId, quantity);
    }

}

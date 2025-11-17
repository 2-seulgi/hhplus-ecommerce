package com.hhplus.be.orderitem.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;  // 주문 시점 스냅샷
    private int unitPrice;  // 주문 시점 단가 스냅샷
    private int quantity;
    private Instant createdAt;

    private OrderItem(Long orderId, Long productId, String productName, int unitPrice, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.createdAt = Instant.now();
    }

    public static OrderItem create(Long orderId, Long productId, String productName, int unitPrice, int quantity) {
        return new OrderItem(orderId, productId, productName, unitPrice, quantity);
    }

    // Mapper용 reconstruct 생성자
    private OrderItem(Long id, Long orderId, Long productId, String productName,
                      int unitPrice, int quantity, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public static OrderItem reconstruct(Long id, Long orderId, Long productId,
                                        String productName, int unitPrice,
                                        int quantity, Instant createdAt) {
        return new OrderItem(id, orderId, productId, productName, unitPrice, quantity, createdAt);
    }

    // 주문 상품의 총액 계산
    public int getTotalPrice() {
        return this.unitPrice * this.quantity;
    }

}
package com.hhplus.be.domain.orderitem;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 200)
    private String productName;  // 주문 시점 스냅샷

    @Column(nullable = false)
    private int unitPrice;  // 주문 시점 단가 스냅샷

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, updatable = false)
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

    // 주문 상품의 총액 계산
    public int getTotalPrice() {
        return this.unitPrice * this.quantity;
    }
}
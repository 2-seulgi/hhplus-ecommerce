package com.hhplus.be.orderitem.domain;

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

    /**
     * ID 할당 (Repository 전용 메서드)
     * JPA 도입 시 제거 예정
     *
     * WARNING: 비즈니스 로직에서 호출 금지!
     * Repository 구현체에서만 사용해야 합니다.
     */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID는 한 번만 할당할 수 있습니다");
        }
        this.id = id;
    }

}
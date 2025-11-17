package com.hhplus.be.orderitem.infrastructure.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "order_items",
    indexes = {
        // 인기 상품 조회 최적화: 커버링 인덱스 (테이블 접근 없이 인덱스만으로 조회)
        @Index(name = "idx_order_item_covering", columnList = "orderId, productId, quantity")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
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

}
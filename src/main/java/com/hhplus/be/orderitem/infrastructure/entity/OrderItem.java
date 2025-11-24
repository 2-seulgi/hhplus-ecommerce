package com.hhplus.be.orderitem.infrastructure.entity;

import com.hhplus.be.order.infrastructure.entity.Order;
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
        // 인기 상품 조회 최적화: 복합 인덱스 (orderId로 JOIN, productId로 GROUP BY)
        @Index(name = "idx_order_item_order_product", columnList = "orderId, productId, quantity")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orderId", nullable = false, updatable = false, insertable = false)
    private Order order;

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
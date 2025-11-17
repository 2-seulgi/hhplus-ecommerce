package com.hhplus.be.order.infrastructure.entity;

import com.hhplus.be.order.domain.model.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "orders",
    indexes = {
        // 인기 상품 조회 최적화: status + paid_at 복합 인덱스
        @Index(name = "idx_order_status_paid", columnList = "status, paidAt"),

        // 주문 목록 조회 최적화: user_id + created_at 복합 인덱스
        @Index(name = "idx_order_user_created", columnList = "userId, createdAt")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private int totalAmount;

    @Column(nullable = false)
    private int finalAmount; // 기본 0, 결제 시 업데이트

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant paidAt;

    @Column
    private Instant canceledAt;

    @Column
    private Instant refundedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

}
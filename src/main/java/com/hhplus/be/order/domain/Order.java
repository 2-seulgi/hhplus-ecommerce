package com.hhplus.be.order.domain;

import com.hhplus.be.common.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Order(Long userId, int totalAmount, Instant expiresAt) {
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public static Order create(Long userId, int totalAmount, Instant expiresAt) {
        return new Order(userId, totalAmount, expiresAt);
    }

    // 결제 완료( PENDING -> CONFIRMED )
    public void confirm() {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException("잘못된 주문 상태입니다", "INVALID_ORDER_STATUS");
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    // 주문 취소 ( PENDING -> CANCELLED )
    public void cancel() {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException("취소할 수 없는 주문 상태입니다.", "INVALID_ORDER_STATUS");
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    // 주문 환불 ( CONFIRMED -> REFUNDED )
    public void refund() {
        if (this.status != OrderStatus.CONFIRMED) {
            throw new BusinessException("환불할 수 없는 주문 상태입니다.", "INVALID_ORDER_STATUS");
        }
        this.status = OrderStatus.REFUNDED;
        this.updatedAt = Instant.now();
    }

    // 주문 만료 여부 확인
    public boolean isExpired(Instant now) {
        return now.isAfter(this.expiresAt) || now.equals(this.expiresAt);
    }

    // 현재 시각 기준 만료 여부
    public boolean isExpired() {
        return isExpired(Instant.now());
    }

}
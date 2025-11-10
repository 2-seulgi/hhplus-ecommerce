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

    private Order(Long userId, int totalAmount, Instant expiresAt) {
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.finalAmount = 0;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Order create(Long userId, int totalAmount, Instant expiresAt) {
        return new Order(userId, totalAmount, expiresAt);
    }

    // 결제 완료( PENDING -> CONFIRMED )
    public void confirm(int finalAmount, Instant paidAt) {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException("잘못된 주문 상태입니다", "INVALID_ORDER_STATUS");
        }
        this.status = OrderStatus.CONFIRMED;
        this.finalAmount = finalAmount;
        this.paidAt = paidAt;
        this.updatedAt = paidAt;
    }

    // 주문 취소 ( PENDING -> CANCELLED )
    public void cancel(Instant now) {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException("취소할 수 없는 주문 상태입니다.", "INVALID_ORDER_STATUS");
        }
        this.status = OrderStatus.CANCELLED;
        this.canceledAt = now;
        this.updatedAt = now;
    }

    // 주문 환불 ( CONFIRMED -> REFUNDED )
    public void refund(Instant now) {
        if (this.status != OrderStatus.CONFIRMED) {
            throw new BusinessException("환불할 수 없는 주문 상태입니다.", "INVALID_ORDER_STATUS");
        }
        this.status = OrderStatus.REFUNDED;
        this.refundedAt = now;
        this.updatedAt = now;
    }

    // 주문 만료 여부 확인
    public boolean isExpired(Instant now) {
        return now.isAfter(this.expiresAt) || now.equals(this.expiresAt);
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
package com.hhplus.be.order.domain.model;

import com.hhplus.be.common.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {
    private Long id;
    private Long userId;
    private OrderStatus status;
    private int totalAmount;
    private int finalAmount; // 기본 0, 결제 시 업데이트
    private Instant expiresAt;
    private Instant paidAt;
    private Instant canceledAt;
    private Instant refundedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private int version;

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

    // Mapper용 reconstruct 생성자
    private Order(Long id, Long userId, OrderStatus status, int totalAmount,
                  int finalAmount, Instant expiresAt, Instant paidAt,
                  Instant canceledAt, Instant refundedAt,
                  Instant createdAt, Instant updatedAt, int version) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.finalAmount = finalAmount;
        this.expiresAt = expiresAt;
        this.paidAt = paidAt;
        this.canceledAt = canceledAt;
        this.refundedAt = refundedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Order reconstruct(Long id, Long userId, OrderStatus status, int totalAmount,
                                    int finalAmount, Instant expiresAt, Instant paidAt,
                                    Instant canceledAt, Instant refundedAt,
                                    Instant createdAt, Instant updatedAt, int version) {
        return new Order(id, userId, status, totalAmount, finalAmount,
                expiresAt, paidAt, canceledAt, refundedAt, createdAt, updatedAt, version);
    }


    // 결제 완료( PENDING -> CONFIRMED )
    public void confirm(int finalAmount, Instant paidAt) {
        if (this.status == OrderStatus.CONFIRMED) {
            throw new BusinessException("이미 결제 완료된 주문입니다", "ORDER_ALREADY_PAID");
        }
        if (this.status == OrderStatus.CANCELLED) {
            throw new BusinessException("취소된 주문은 결제할 수 없습니다", "ORDER_CANCELLED");
        }
        if (this.status == OrderStatus.REFUNDED) {
            throw new BusinessException("환불된 주문은 결제할 수 없습니다", "ORDER_REFUNDED");
        }
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException("결제할 수 없는 주문 상태입니다: " + this.status, "INVALID_ORDER_STATUS");
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

}
package com.hhplus.be.order.domain.model;

import com.hhplus.be.common.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    // ====== 도메인 필드 ======
    private Long id;
    private Long userId;
    private OrderStatus status;
    private int totalAmount;
    private int finalAmount; // 결제 시 확정 금액

    private Instant expiresAt;   // 주문 만료 시각
    private Instant paidAt;
    private Instant canceledAt;
    private Instant refundedAt;
    private Instant createdAt;
    private Instant updatedAt;

    // 주문 결제 유효 시간 (예: 30분)
    private static final long PAYMENT_TTL_SECONDS = 30 * 60;

    // ====== 생성 로직 ======

    /**
     * 신규 주문 생성 (PENDING 상태)
     *
     * - now 기준으로 createdAt / updatedAt 설정
     * - 만료 시각은 now + TTL 로 계산
     */
    private Order(Long userId, int totalAmount, Instant now) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (totalAmount <= 0) {
            throw new IllegalArgumentException("주문 금액은 양수여야 합니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다");
        }

        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.finalAmount = 0;

        this.createdAt = now;
        this.updatedAt = now;
        this.expiresAt = now.plusSeconds(PAYMENT_TTL_SECONDS);
    }

    /**
     * 신규 주문 팩토리
     */
    public static Order create(Long userId, int totalAmount, Instant now) {
        return new Order(userId, totalAmount, now);
    }

    /**
     * Mapper에서 사용하는 복원용 팩토리 (DB → Domain)
     *
     * - DB에서 읽어온 값들을 그대로 넣되,
     *   도메인 invariant가 깨지지 않도록 최소한의 방어만 한다.
     */
    private Order(Long id,
                  Long userId,
                  OrderStatus status,
                  int totalAmount,
                  int finalAmount,
                  Instant expiresAt,
                  Instant paidAt,
                  Instant canceledAt,
                  Instant refundedAt,
                  Instant createdAt,
                  Instant updatedAt) {

        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (status == null) {
            throw new IllegalArgumentException("주문 상태는 필수입니다");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("생성 시각은 필수입니다");
        }

        // expiresAt 이 null 이면 최소한 createdAt 기준으로 보정
        if (expiresAt == null) {
            expiresAt = createdAt.plusSeconds(PAYMENT_TTL_SECONDS);
        }

        if (updatedAt == null) {
            updatedAt = createdAt;
        }

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
    }

    public static Order reconstruct(Long id,
                                    Long userId,
                                    OrderStatus status,
                                    int totalAmount,
                                    int finalAmount,
                                    Instant expiresAt,
                                    Instant paidAt,
                                    Instant canceledAt,
                                    Instant refundedAt,
                                    Instant createdAt,
                                    Instant updatedAt) {

        return new Order(
                id,
                userId,
                status,
                totalAmount,
                finalAmount,
                expiresAt,
                paidAt,
                canceledAt,
                refundedAt,
                createdAt,
                updatedAt
        );
    }

    // ====== 상태 전이 메서드 ======

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

        if (paidAt == null) {
            throw new IllegalArgumentException("결제 시각은 필수입니다");
        }
        if (finalAmount < 0) {
            throw new IllegalArgumentException("최종 금액은 음수일 수 없습니다");
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
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다");
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
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다");
        }
        this.status = OrderStatus.REFUNDED;
        this.refundedAt = now;
        this.updatedAt = now;
    }

    // 주문 만료 여부 확인
    public boolean isExpired(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다");
        }
        return !now.isBefore(this.expiresAt); // now >= expiresAt
    }
}

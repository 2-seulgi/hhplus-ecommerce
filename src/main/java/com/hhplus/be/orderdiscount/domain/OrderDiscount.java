package com.hhplus.be.orderdiscount.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "order_discounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column
    private Long userCouponId;  // 쿠폰 할인일 경우, nullable

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType discountType;

    @Column(nullable = false)
    private int discountValue;  // 할인 정책 값 (예: 5000원 또는 10%)

    @Column(nullable = false)
    private int discountAmount;  // 실제 차감된 금액

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private OrderDiscount(Long orderId, Long userCouponId, DiscountType discountType,
                          int discountValue, int discountAmount) {
        this.orderId = orderId;
        this.userCouponId = userCouponId;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.discountAmount = discountAmount;
        this.createdAt = Instant.now();
    }

    // 쿠폰 할인 생성
    public static OrderDiscount createCouponDiscount(Long orderId, Long userCouponId,
                                                      int discountValue, int discountAmount) {
        return new OrderDiscount(orderId, userCouponId, DiscountType.COUPON,
                                 discountValue, discountAmount);
    }

    // 포인트 할인 생성
    public static OrderDiscount createPointDiscount(Long orderId, int discountValue, int discountAmount) {
        return new OrderDiscount(orderId, null, DiscountType.POINT,
                                 discountValue, discountAmount);
    }
}
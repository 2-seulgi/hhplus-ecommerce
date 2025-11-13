package com.hhplus.be.orderdiscount.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDiscount {
    private Long id;
    private Long orderId;
    private Long userCouponId;  // 쿠폰 할인일 경우, nullable
    private DiscountType discountType;
    private int discountValue;  // 할인 정책 값 (예: 5000원 또는 10%)
    private int discountAmount;  // 실제 차감된 금액
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

    // Mapper용 reconstruct 생성자
    private OrderDiscount(Long id, Long orderId, Long userCouponId, DiscountType discountType,
                          int discountValue, int discountAmount, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.userCouponId = userCouponId;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.discountAmount = discountAmount;
        this.createdAt = createdAt;
    }

    public static OrderDiscount reconstruct(Long id, Long orderId, Long userCouponId,
                                            DiscountType discountType, int discountValue,
                                            int discountAmount, Instant createdAt) {
        return new OrderDiscount(id, orderId, userCouponId, discountType,
                                 discountValue, discountAmount, createdAt);
    }
}
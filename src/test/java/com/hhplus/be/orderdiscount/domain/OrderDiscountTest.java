package com.hhplus.be.orderdiscount.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDiscountTest {

    @Test
    @DisplayName("쿠폰 할인 정보를 생성할 수 있다")
    void createCouponDiscount() {
        // given & when
        OrderDiscount discount = OrderDiscount.createCouponDiscount(
                1L,     // orderId
                10L,    // userCouponId
                5000,   // discountValue
                5000    // discountAmount
        );

        // then
        assertThat(discount.getOrderId()).isEqualTo(1L);
        assertThat(discount.getUserCouponId()).isEqualTo(10L);
        assertThat(discount.getDiscountType()).isEqualTo(DiscountType.COUPON);
        assertThat(discount.getDiscountValue()).isEqualTo(5000);
        assertThat(discount.getDiscountAmount()).isEqualTo(5000);
    }

    @Test
    @DisplayName("포인트 할인 정보를 생성할 수 있다")
    void createPointDiscount() {
        // given & when
        OrderDiscount discount = OrderDiscount.createPointDiscount(
                1L,     // orderId
                3000,   // discountAmount
                3000    // discountAmount
        );

        // then
        assertThat(discount.getOrderId()).isEqualTo(1L);
        assertThat(discount.getUserCouponId()).isNull();
        assertThat(discount.getDiscountType()).isEqualTo(DiscountType.POINT);
        assertThat(discount.getDiscountValue()).isEqualTo(3000);
        assertThat(discount.getDiscountAmount()).isEqualTo(3000);
    }
}
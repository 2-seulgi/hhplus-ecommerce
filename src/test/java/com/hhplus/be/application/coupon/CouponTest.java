package com.hhplus.be.application.coupon;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.domain.coupon.Coupon;
import com.hhplus.be.domain.coupon.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    @Test
    @DisplayName("쿠폰 발급 수량을 증가시킬 수 있다")
    void increaseIssued() {
        // given
        Coupon coupon = createCoupon(100, 50);

        // when
        coupon.increaseIssued();

        // then
        assertThat(coupon.getIssuedQuantity()).isEqualTo(51);
    }


    @Test
    @DisplayName("발급 수량이 모두 소진되면 발급할 수 없다")
    void cannotIssueWhenSoldOut() {
        // given
        Coupon coupon = createCoupon(100, 100);

        // when & then
        assertThatThrownBy(coupon::increaseIssued)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("쿠폰이 모두 소진되었습니다");
    }

    @Test
    @DisplayName("발급 가능 여부를 확인할 수 있다")
    void canIssue() {
        // given
        Coupon available = createCoupon(100, 50);
        Coupon soldOut = createCoupon(100, 100);

        // when & then
        assertThat(available.canIssue()).isTrue();
        assertThat(soldOut.canIssue()).isFalse();
    }

    private Coupon createCoupon(int totalQuantity, int issuedQuantity) {
        Instant now = Instant.now();
        return Coupon.create(
                "WELCOME10",
                "신규 회원 쿠폰",
                DiscountType.FIXED,
                5000,
                totalQuantity,
                issuedQuantity,
                now,
                now.plus(7, ChronoUnit.DAYS),
                now,
                now.plus(1, ChronoUnit.DAYS)
        );
    }
}
package com.hhplus.be.domain.usercoupon;

import com.hhplus.be.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserCouponTest {

    @Test
    @DisplayName("사용자 쿠폰을 생성할 수 있다")
    void createUserCoupon() {
        // given
        Instant issuedAt = Instant.now();

        // when
        UserCoupon userCoupon = UserCoupon.create(1L, 100L, issuedAt);

        // then
        assertThat(userCoupon.getUserId()).isEqualTo(1L);
        assertThat(userCoupon.getCouponId()).isEqualTo(100L);
        assertThat(userCoupon.isUsed()).isFalse();
        assertThat(userCoupon.getIssuedAt()).isEqualTo(issuedAt);
    }

    @Test
    @DisplayName("쿠폰을 사용 처리할 수 있다")
    void useCoupon() {
        // given
        UserCoupon userCoupon = UserCoupon.create(1L, 100L, Instant.now());

        // when
        userCoupon.use();

        // then
        assertThat(userCoupon.isUsed()).isTrue();
        assertThat(userCoupon.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 사용한 쿠폰은 다시 사용할 수 없다")
    void cannotUseAlreadyUsedCoupon() {
        // given
        UserCoupon userCoupon = UserCoupon.create(1L, 100L, Instant.now());
        userCoupon.use();

        // when & then
        assertThatThrownBy(() -> userCoupon.use())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 사용된 쿠폰입니다");
    }
}
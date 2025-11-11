package com.hhplus.be.user.domain;

import com.hhplus.be.common.exception.InsufficientBalanceException;
import com.hhplus.be.common.exception.InvalidInputException;
import com.hhplus.be.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    // 사용자는 이미 등록되어 있다고 가정 (API 명세에 사용자 등록 API 없음)
    // 포인트 관리 로직만 테스트

    @Test
    @DisplayName("포인트를 충전할 수 있다")
    void chargePoint() {
        // given
        User user = User.create(1L,"홍길동", "hong@example.com", 10000);

        // when
        user.charge(5000);

        // then
        assertThat(user.getBalance()).isEqualTo(15000);
    }

    @Test
    @DisplayName("포인트 충전 금액은 양수여야 한다")
    void chargeAmountMustBePositive() {
        // given
        User user = User.create(1L,"홍길동", "hong@example.com", 10000);

        // when & then
        assertThatThrownBy(() -> user.charge(0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("충전 금액은 양수여야 합니다");

        assertThatThrownBy(() -> user.charge(-1000))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("충전 금액은 양수여야 합니다");
    }

    @Test
    @DisplayName("포인트 충전 금액은 1000원 이상이어야 한다")
    void chargeAmountMustBeAtLeast1000() {
        // given
        User user = User.create(1L, "홍길동", "hong@example.com", 10000);

        // when & then
        assertThatThrownBy(() -> user.charge(999))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("최소 충전 금액은 1000원입니다");
    }

    @Test
    @DisplayName("포인트 충전 금액은 1,000,000원 이하여야 한다")
    void chargeAmountMustBeAtMost1000000() {
        // given
        User user = User.create(1L, "홍길동", "hong@example.com", 10000);

        // when & then
        assertThatThrownBy(() -> user.charge(1000001))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("최대 충전 금액은 1,000,000원입니다");
    }

    @Test
    @DisplayName("포인트를 사용할 수 있다")
    void usePoint() {
        // given
        User user = User.create(1L,"홍길동", "hong@example.com", 10000);

        // when
        user.use(3000);

        // then
        assertThat(user.getBalance()).isEqualTo(7000);
    }

    @Test
    @DisplayName("포인트 사용 금액은 양수여야 한다")
    void useAmountMustBePositive() {
        // given
        User user = User.create(1L,"홍길동", "hong@example.com", 10000);

        // when & then
        assertThatThrownBy(() -> user.use(0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("사용 금액은 양수여야 합니다");

        assertThatThrownBy(() -> user.use(-1000))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("사용 금액은 양수여야 합니다");
    }

    @Test
    @DisplayName("잔액이 부족하면 포인트를 사용할 수 없다")
    void cannotUsePointWhenInsufficientBalance() {
        // given
        User user = User.create(1L, "홍길동", "hong@example.com", 5000);

        // when & then
        assertThatThrownBy(() -> user.use(10000))
            .isInstanceOf(InsufficientBalanceException.class)
            .hasMessageContaining("잔액이 부족합니다");
    }

    @Test
    @DisplayName("포인트를 환불할 수 있다")
    void refundPoint() {
        // given
        User user = User.create(1L,"홍길동", "hong@example.com", 5000);

        // when
        user.refund(3000);

        // then
        assertThat(user.getBalance()).isEqualTo(8000);
    }

    @Test
    @DisplayName("환불 금액은 양수여야 한다")
    void refundAmountMustBePositive() {
        // given
        User user = User.create(1L,"홍길동", "hong@example.com", 5000);

        // when & then
        assertThatThrownBy(() -> user.refund(0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("환불 금액은 양수여야 합니다");

        assertThatThrownBy(() -> user.refund(-1000))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("환불 금액은 양수여야 합니다");
    }
}

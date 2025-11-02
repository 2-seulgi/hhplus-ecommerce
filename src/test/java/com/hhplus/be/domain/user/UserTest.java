package com.hhplus.be.domain.user;

import com.hhplus.be.common.exception.InsufficientBalanceException;
import com.hhplus.be.common.exception.InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    @DisplayName("사용자를 생성할 수 있다")
    void createUser() {
        // given
        String name = "홍길동";
        String email = "hong@example.com";
        int initialBalance = 0;

        // when
        User user = User.create(name, email, initialBalance);

        // then
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getEmail()).isEqualTo("hong@example.com");
        assertThat(user.getBalance()).isEqualTo(0);
        assertThat(user.getVersion()).isEqualTo(0);
    }

    @Test
    @DisplayName("사용자 이름은 필수다")
    void userNameIsRequired() {
        // when & then
        assertThatThrownBy(() -> User.create(null, "test@example.com", 0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("이름은 필수입니다");

        assertThatThrownBy(() -> User.create("", "test@example.com", 0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("이름은 필수입니다");
    }

    @Test
    @DisplayName("이메일은 필수다")
    void emailIsRequired() {
        // when & then
        assertThatThrownBy(() -> User.create("홍길동", null, 0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("이메일은 필수입니다");

        assertThatThrownBy(() -> User.create("홍길동", "", 0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("이메일은 필수입니다");
    }

    @Test
    @DisplayName("초기 잔액은 0 이상이어야 한다")
    void initialBalanceMustBeNonNegative() {
        // when & then
        assertThatThrownBy(() -> User.create("홍길동", "hong@example.com", -1000))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("잔액은 0 이상이어야 합니다");
    }

    @Test
    @DisplayName("포인트를 충전할 수 있다")
    void chargePoint() {
        // given
        User user = User.create("홍길동", "hong@example.com", 10000);

        // when
        user.charge(5000);

        // then
        assertThat(user.getBalance()).isEqualTo(15000);
    }

    @Test
    @DisplayName("포인트 충전 금액은 양수여야 한다")
    void chargeAmountMustBePositive() {
        // given
        User user = User.create("홍길동", "hong@example.com", 10000);

        // when & then
        assertThatThrownBy(() -> user.charge(0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("충전 금액은 양수여야 합니다");

        assertThatThrownBy(() -> user.charge(-1000))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("충전 금액은 양수여야 합니다");
    }

    @Test
    @DisplayName("포인트를 사용할 수 있다")
    void usePoint() {
        // given
        User user = User.create("홍길동", "hong@example.com", 10000);

        // when
        user.use(3000);

        // then
        assertThat(user.getBalance()).isEqualTo(7000);
    }

    @Test
    @DisplayName("포인트 사용 금액은 양수여야 한다")
    void useAmountMustBePositive() {
        // given
        User user = User.create("홍길동", "hong@example.com", 10000);

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
        User user = User.create("홍길동", "hong@example.com", 5000);

        // when & then
        assertThatThrownBy(() -> user.use(10000))
            .isInstanceOf(InsufficientBalanceException.class)
            .hasMessageContaining("잔액이 부족합니다");
    }

    @Test
    @DisplayName("포인트를 환불할 수 있다")
    void refundPoint() {
        // given
        User user = User.create("홍길동", "hong@example.com", 5000);

        // when
        user.refund(3000);

        // then
        assertThat(user.getBalance()).isEqualTo(8000);
    }

    @Test
    @DisplayName("환불 금액은 양수여야 한다")
    void refundAmountMustBePositive() {
        // given
        User user = User.create("홍길동", "hong@example.com", 5000);

        // when & then
        assertThatThrownBy(() -> user.refund(0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("환불 금액은 양수여야 합니다");

        assertThatThrownBy(() -> user.refund(-1000))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("환불 금액은 양수여야 합니다");
    }
}

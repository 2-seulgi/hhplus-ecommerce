package com.hhplus.be.domain.point;

import com.hhplus.be.common.exception.InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PointTest {

    @Test
    @DisplayName("포인트 충전 내역을 생성할 수 있다")
    void createChargePoint() {
        // given
        Long userId = 1L;
        int amount = 10000;
        int balanceAfter = 10000;

        // when
        Point point = Point.charge(userId, amount, balanceAfter);

        // then
        assertThat(point.getUserId()).isEqualTo(1L);
        assertThat(point.getPointType()).isEqualTo(PointType.CHARGE);
        assertThat(point.getAmount()).isEqualTo(10000);
        assertThat(point.getBalanceAfter()).isEqualTo(10000);
    }

    @Test
    @DisplayName("포인트 사용 내역을 생성할 수 있다")
    void createUsePoint() {
        // given
        Long userId = 1L;
        int amount = 5000;
        int balanceAfter = 5000;

        // when
        Point point = Point.use(userId, amount, balanceAfter);

        // then
        assertThat(point.getUserId()).isEqualTo(1L);
        assertThat(point.getPointType()).isEqualTo(PointType.USE);
        assertThat(point.getAmount()).isEqualTo(5000);
        assertThat(point.getBalanceAfter()).isEqualTo(5000);
    }

    @Test
    @DisplayName("포인트 환불 내역을 생성할 수 있다")
    void createRefundPoint() {
        // given
        Long userId = 1L;
        int amount = 3000;
        int balanceAfter = 8000;

        // when
        Point point = Point.refund(userId, amount, balanceAfter);

        // then
        assertThat(point.getUserId()).isEqualTo(1L);
        assertThat(point.getPointType()).isEqualTo(PointType.REFUND);
        assertThat(point.getAmount()).isEqualTo(3000);
        assertThat(point.getBalanceAfter()).isEqualTo(8000);
    }

    @Test
    @DisplayName("userId는 필수다")
    void userIdIsRequired() {
        // when & then
        assertThatThrownBy(() -> Point.charge(null, 10000, 10000))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("사용자 ID는 필수입니다");
    }

    @Test
    @DisplayName("금액은 양수여야 한다")
    void amountMustBePositive() {
        // when & then
        assertThatThrownBy(() -> Point.charge(1L, 0, 0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("금액은 양수여야 합니다");

        assertThatThrownBy(() -> Point.charge(1L, -1000, 0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("금액은 양수여야 합니다");
    }

    @Test
    @DisplayName("거래 후 잔액은 0 이상이어야 한다")
    void balanceAfterMustBeNonNegative() {
        // when & then
        assertThatThrownBy(() -> Point.charge(1L, 10000, -1000))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("거래 후 잔액은 0 이상이어야 합니다");
    }
}
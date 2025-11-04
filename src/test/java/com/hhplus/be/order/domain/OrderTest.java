package com.hhplus.be.order.domain;

import com.hhplus.be.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    @DisplayName("주문을 확정할 수 있다.")
    void confirmOrder() {
        // given
        Order order = Order.create(1L, 30000, Instant.now().plus(30, ChronoUnit.MINUTES ));
        // when
        order.confirm();
        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("주문을 취소할 수 있다.")
    void cancelOrder() {
        //given
        Order order = Order.create(1L, 30000, Instant.now().plus(30, ChronoUnit.MINUTES ));
        // when
        order.cancel();
        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("PENDING 상태가 아니면 취소할 수 없다.")
    void cannotCancelIfNotPending() {
        // given
        Order order = Order.create(1L, 30000, Instant.now().plus(30, ChronoUnit.MINUTES ));
        order.confirm();
        // when & then
        assertThatThrownBy(order::cancel)
                .isInstanceOf(BusinessException.class)
                .hasMessage("취소할 수 없는 주문 상태입니다.");
    }

    @Test
    @DisplayName("주문을 환불할 수 있다")
    void refundOrder() {
        // given
        Order order = Order.create(1L, 30000, Instant.now().plus(30, ChronoUnit.MINUTES ));
        order.confirm();

        // when
        order.refund();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("CONFIRMED 상태가 아니면 환불할 수 없다")
    void cannotRefundIfNotConfirmed() {
        // given
        Order order = Order.create(1L, 30000, Instant.now().plus(30, ChronoUnit.MINUTES ));

        // when & then
        assertThatThrownBy(() -> order.refund())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("환불할 수 없는 주문 상태입니다");
    }

    @Test
    @DisplayName("주문 만료 여부를 확인할 수 있다 (경계 포함: now >= expiresAt → 만료)")
    void checkExpired() {
        // 고정된 현재시각
        Clock fixed = Clock.fixed(Instant.parse("2025-10-29T10:00:00Z"), ZoneOffset.UTC);
        Instant now = Instant.now(fixed);

        // given
        Instant expiredAt = now.minus(Duration.ofMinutes(1));   //  과거 → 만료
        Order expiredOrder = Order.create(1L, 30000, expiredAt);

        Instant validAt = now.plus(Duration.ofMinutes(30));     //  미래 → 유효
        Order validOrder = Order.create(1L, 30000, validAt);

        // 경계값: now == expiresAt → 만료로 간주
        Order boundaryOrder = Order.create(1L, 30000, now);

        // when & then
        assertThat(expiredOrder.isExpired(now)).isTrue();
        assertThat(validOrder.isExpired(now)).isFalse();
        assertThat(boundaryOrder.isExpired(now)).isTrue();       // 규칙: now >= expiresAt ⇒ 만료
    }

}
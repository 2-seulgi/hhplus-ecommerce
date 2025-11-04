package com.hhplus.be.cart.domain;

import com.hhplus.be.common.exception.InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartItemTest {

    @Test
    @DisplayName("장바구니 아이템을 생성할 수 있다")
    void createCartItem() {
        // given & when
        CartItem cartItem = CartItem.create(1L, 100L, 3);

        // then
        assertThat(cartItem.getUserId()).isEqualTo(1L);
        assertThat(cartItem.getProductId()).isEqualTo(100L);
        assertThat(cartItem.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("수량을 변경할 수 있다")
    void changeQuantity() {
        // given
        CartItem cartItem = CartItem.create(1L, 100L, 3);

        // when
        cartItem.changeQuantity(5);

        // then
        assertThat(cartItem.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("수량은 1 이상이어야 한다")
    void quantityMustBePositive() {
        // given
        CartItem cartItem = CartItem.create(1L, 100L, 3);

        // when & then
        assertThatThrownBy(() -> cartItem.changeQuantity(0))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("수량은 1 이상이어야 합니다");

        assertThatThrownBy(() -> cartItem.changeQuantity(-1))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("수량은 1 이상이어야 합니다");
    }

    @Test
    @DisplayName("생성 시 수량은 1 이상이어야 한다")
    void createWithInvalidQuantity() {
        // when & then
        assertThatThrownBy(() -> CartItem.create(1L, 100L, 0))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("수량은 1 이상이어야 합니다");

        assertThatThrownBy(() -> CartItem.create(1L, 100L, -5))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("수량은 1 이상이어야 합니다");
    }
}
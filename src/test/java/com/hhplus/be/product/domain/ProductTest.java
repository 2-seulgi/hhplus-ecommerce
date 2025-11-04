package com.hhplus.be.product.domain;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ProductTest {

    @Test
    @DisplayName("재고를 차감할 수 있다")
    void decreaseStock() {
        // given
        Product product = Product.create("맥북 PRO", "M3", 3000000, 10);

        // when
        product.decreaseStock(3);

        // then
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("재고가 부족하면 차감할 수 없다")
    void cannotDecreaseStockWhenOutOfStock() {
        // given
        Product product = Product.create("맥북 PRO", "M3", 3000000, 10);

        // when & then
        assertThatThrownBy(() -> product.decreaseStock(15))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("재고 차감 수량은 양수여야 한다")
    void  decreaseQuantityMustBePositive(){
        // given
        Product product = Product.create("맥북 PRO", "M3", 3000000, 10);

        // when & then
        assertThatThrownBy(() -> product.decreaseStock(0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("차감 수량은 양수여야 합니다");

        assertThatThrownBy(() -> product.decreaseStock(-5))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("차감 수량은 양수여야 합니다");
    }

    @Test
    @DisplayName("재고를 복원할 수 있다")
    void increaseStock() {
        // given
        Product product = Product.create("맥북 PRO", "M3", 3000000, 10);

        // when
        product.increaseStock(5);

        // then
        assertThat(product.getStock()).isEqualTo(15);
    }

    @Test
    @DisplayName("재고 복원 수량은 양수여야 한다")
    void increaseQuantityMustBePositive(){
        // given
        Product product = Product.create("맥북 PRO", "M3", 3000000, 10);

        // when & then
        assertThatThrownBy(() -> product.increaseStock(0))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("복원 수량은 양수여야 합니다");

        assertThatThrownBy(() -> product.increaseStock(-5))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("복원 수량은 양수여야 합니다");
    }


}

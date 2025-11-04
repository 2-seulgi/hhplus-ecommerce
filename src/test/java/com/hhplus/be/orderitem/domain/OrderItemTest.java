package com.hhplus.be.orderitem.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    @Test
    @DisplayName("주문 상품을 생성할 수 있다")
    void createOrderItem() {
        // given & when
        OrderItem orderItem = OrderItem.create(
                1L,           // orderId
                100L,         // productId
                "맥북 프로",    // productName (스냅샷)
                3000000,      // unitPrice (스냅샷)
                2             // quantity
        );

        // then
        assertThat(orderItem.getOrderId()).isEqualTo(1L);
        assertThat(orderItem.getProductId()).isEqualTo(100L);
        assertThat(orderItem.getProductName()).isEqualTo("맥북 프로");
        assertThat(orderItem.getUnitPrice()).isEqualTo(3000000);
        assertThat(orderItem.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("주문 상품의 총액을 계산할 수 있다")
    void calculateTotalPrice() {
        // given
        OrderItem orderItem = OrderItem.create(
                1L, 100L, "맥북 프로", 3000000, 2
        );

        // when
        int totalPrice = orderItem.getTotalPrice();

        // then
        assertThat(totalPrice).isEqualTo(6000000);
    }
}
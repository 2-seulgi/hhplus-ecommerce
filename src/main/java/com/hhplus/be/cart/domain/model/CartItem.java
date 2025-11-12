package com.hhplus.be.cart.domain.model;

import com.hhplus.be.common.exception.InvalidInputException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    private Long id;
    private Long userId;
    private Long productId;
    private int quantity;
    private Instant createdAt;
    private Instant updatedAt;

    private CartItem(Long userId, Long productId, int quantity) {

        validateQuantity(quantity);
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public static CartItem create(Long userId, Long productId, int quantity) {
         return new CartItem(userId, productId, quantity);
    }

    public static CartItem reconstruct(Long id, Long userId, Long productId, int quantity,
                                       Instant createdAt, Instant updatedAt) {
        return new CartItem(id, userId, productId, quantity, createdAt, updatedAt);
    }

    public void changeQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new InvalidInputException("수량은 1 이상이어야 합니다");
            // TODO: 수량 초과 관련 유효성 체크 추가
        }
    }
}
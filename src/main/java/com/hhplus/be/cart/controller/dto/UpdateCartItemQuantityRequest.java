package com.hhplus.be.cart.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 수량 변경 Request
 * API: PATCH /users/{userId}/cart/items/{cartItemId}
 */
public record UpdateCartItemQuantityRequest(
        @NotNull(message = "수량은 필수입니다")
        @Min(value = 0, message = "수량은 0 이상이어야 합니다")
        Integer quantity
) {
}
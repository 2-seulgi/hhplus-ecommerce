package com.hhplus.be.cart.service.dto;

/**
 * 장바구니 수량 변경 Command
 * API: PATCH /users/{userId}/cart/items/{cartItemId}
 */
public record UpdateCartItemQuantityCommand(
        Long userId,
        Long cartItemId,
        int quantity
) {
}
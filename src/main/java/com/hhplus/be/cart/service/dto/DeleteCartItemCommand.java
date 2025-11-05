package com.hhplus.be.cart.service.dto;

/**
 * 장바구니 삭제 Command
 * API: DELETE /users/{userId}/cart/items/{cartItemId}
 */
public record DeleteCartItemCommand(
        Long userId,
        Long cartItemId
) {
}
package com.hhplus.be.cart.service.dto;

/**
 * 장바구니 담기 Command
 * API: POST /users/{userId}/cart/items
 */
public record AddCartItemCommand(
        Long userId,
        Long productId,
        int quantity
) {
}
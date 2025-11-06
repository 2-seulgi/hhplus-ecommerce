package com.hhplus.be.cart.service.dto;

/**
 * 장바구니 담기 Result
 * API: POST /users/{userId}/cart/items
 */
public record AddCartItemResult(
        CartResult.CartItemInfo item,
        CartResult.CartSummary summary
) {
}
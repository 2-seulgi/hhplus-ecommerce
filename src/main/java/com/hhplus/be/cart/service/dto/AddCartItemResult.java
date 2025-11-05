package com.hhplus.be.cart.service.dto;

import lombok.Getter;

/**
 * 장바구니 담기 Result
 * API: POST /users/{userId}/cart/items
 */
@Getter
public class AddCartItemResult {
    private final CartResult.CartItemInfo item;
    private final CartResult.CartSummary summary;

    public AddCartItemResult(CartResult.CartItemInfo item, CartResult.CartSummary summary) {
        this.item = item;
        this.summary = summary;
    }
}
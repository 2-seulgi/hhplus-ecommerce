package com.hhplus.be.cart.controller.dto;

import com.hhplus.be.cart.service.dto.CartResult;

import java.util.List;

/**
 * 장바구니 조회 Response
 * API: GET /users/{userId}/cart/items
 * API: DELETE /users/{userId}/cart/items/{cartItemId}
 * API: PATCH /users/{userId}/cart/items/{cartItemId}
 */
public record CartResponse(
        List<CartItemInfo> items,
        CartSummary summary
) {
    public record CartItemInfo(
            Long cartItemId,
            Long productId,
            String productName,
            int unitPrice,
            int quantity,
            int subtotal,
            int stock,
            boolean stockOk
    ) {
    }

    public record CartSummary(
            int totalAmount,
            int itemCount
    ) {
    }

    public static CartResponse from(CartResult result) {
        var items = result.items().stream()
                .map(item -> new CartItemInfo(
                        item.cartItemId(),
                        item.productId(),
                        item.productName(),
                        item.unitPrice(),
                        item.quantity(),
                        item.subtotal(),
                        item.stock(),
                        item.stockOk()
                ))
                .toList();

        var summary = new CartSummary(
                result.summary().totalAmount(),
                result.summary().itemCount()
        );

        return new CartResponse(items, summary);
    }
}
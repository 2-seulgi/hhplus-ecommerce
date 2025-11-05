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
        var items = result.getItems().stream()
                .map(item -> new CartItemInfo(
                        item.getCartItemId(),
                        item.getProductId(),
                        item.getProductName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getSubtotal(),
                        item.getStock(),
                        item.isStockOk()
                ))
                .toList();

        var summary = new CartSummary(
                result.getSummary().getTotalAmount(),
                result.getSummary().getItemCount()
        );

        return new CartResponse(items, summary);
    }
}
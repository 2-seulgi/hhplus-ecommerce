package com.hhplus.be.cart.controller.dto;

import com.hhplus.be.cart.service.dto.AddCartItemResult;

/**
 * 장바구니 담기 Response
 * API: POST /users/{userId}/cart/items
 */
public record AddCartItemResponse(
        CartItemInfo item,
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

    public static AddCartItemResponse from(AddCartItemResult result) {
        var item = result.item();
        var summary = result.summary();

        return new AddCartItemResponse(
                new CartItemInfo(
                        item.cartItemId(),
                        item.productId(),
                        item.productName(),
                        item.unitPrice(),
                        item.quantity(),
                        item.subtotal(),
                        item.stock(),
                        item.stockOk()
                ),
                new CartSummary(
                        summary.totalAmount(),
                        summary.itemCount()
                )
        );
    }
}
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
        var item = result.getItem();
        var summary = result.getSummary();

        return new AddCartItemResponse(
                new CartItemInfo(
                        item.getCartItemId(),
                        item.getProductId(),
                        item.getProductName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getSubtotal(),
                        item.getStock(),
                        item.isStockOk()
                ),
                new CartSummary(
                        summary.getTotalAmount(),
                        summary.getItemCount()
                )
        );
    }
}
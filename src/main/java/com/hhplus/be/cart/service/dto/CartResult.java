package com.hhplus.be.cart.service.dto;

import com.hhplus.be.cart.domain.model.CartItem;
import com.hhplus.be.product.domain.model.Product;

import java.util.List;

/**
 * 장바구니 조회 Result
 * API: GET /users/{userId}/cart/items
 */
public record CartResult(
        List<CartItemInfo> items,
        CartSummary summary
) {
    public CartResult(List<CartItemInfo> items) {
        this(items, calculateSummary(items));
    }

    private static CartSummary calculateSummary(List<CartItemInfo> items) {
        int totalAmount = items.stream()
                .mapToInt(CartItemInfo::subtotal)
                .sum();
        int itemCount = items.size();
        return new CartSummary(totalAmount, itemCount);
    }

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
        private CartItemInfo(Long cartItemId, Long productId, String productName, int unitPrice, int quantity, int stock) {
            this(
                    cartItemId,
                    productId,
                    productName,
                    unitPrice,
                    quantity,
                    unitPrice * quantity,
                    stock,
                    stock >= quantity
            );
        }

        public static CartItemInfo from(CartItem cartItem, Product product) {
            return new CartItemInfo(
                    cartItem.getId(),
                    cartItem.getProductId(),
                    product.getName(),
                    product.getPrice(),
                    cartItem.getQuantity(),
                    product.getStock()
            );
        }
    }

    public record CartSummary(
            int totalAmount,
            int itemCount
    ) {
    }
}
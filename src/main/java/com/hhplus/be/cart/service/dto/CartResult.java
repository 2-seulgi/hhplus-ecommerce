package com.hhplus.be.cart.service.dto;

import com.hhplus.be.cart.domain.CartItem;
import com.hhplus.be.product.domain.Product;
import lombok.Getter;

import java.util.List;

/**
 * 장바구니 조회 Result
 * API: GET /users/{userId}/cart/items
 */
@Getter
public class CartResult {
    private final List<CartItemInfo> items;
    private final CartSummary summary;

    public CartResult(List<CartItemInfo> items) {
        this.items = items;
        this.summary = calculateSummary(items);
    }

    private CartSummary calculateSummary(List<CartItemInfo> items) {
        int totalAmount = items.stream()
                .mapToInt(CartItemInfo::getSubtotal)
                .sum();
        int itemCount = items.size();
        return new CartSummary(totalAmount, itemCount);
    }

    @Getter
    public static class CartItemInfo {
        private final Long cartItemId;
        private final Long productId;
        private final String productName;
        private final int unitPrice;
        private final int quantity;
        private final int subtotal;
        private final int stock;
        private final boolean stockOk;

        private CartItemInfo(Long cartItemId, Long productId, String productName, int unitPrice, int quantity, int stock) {
            this.cartItemId = cartItemId;
            this.productId = productId;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
            this.subtotal = unitPrice * quantity;
            this.stock = stock;
            this.stockOk = stock >= quantity;
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

    @Getter
    public static class CartSummary {
        private final int totalAmount;
        private final int itemCount;

        public CartSummary(int totalAmount, int itemCount) {
            this.totalAmount = totalAmount;
            this.itemCount = itemCount;
        }
    }
}
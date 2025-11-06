package com.hhplus.be.product.service.dto;

import com.hhplus.be.product.domain.Product;
import com.hhplus.be.product.domain.StockStatus;

/**
 * 상품 재고 조회 Result
 * API: GET /products/{productId}/stock
 */
public record ProductStockResult(
        Long productId,
        int stock,
        StockStatus stockStatus
) {
    public static ProductStockResult from(Product product) {
        return new ProductStockResult(
                product.getProduct_id(),
                product.getStock(),
                product.getStockStatus()
        );
    }
}
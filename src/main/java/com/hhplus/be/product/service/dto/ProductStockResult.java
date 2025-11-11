package com.hhplus.be.product.service.dto;

import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.domain.model.StockStatus;

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
                product.getId(),
                product.getStock(),
                product.getStockStatus()
        );
    }
}
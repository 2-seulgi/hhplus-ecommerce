package com.hhplus.be.product.controller.dto;

import com.hhplus.be.product.domain.model.StockStatus;
import com.hhplus.be.product.service.dto.ProductStockResult;

/**
 * 상품 재고 조회 Response
 * API: GET /products/{productId}/stock
 *
 * API 명세 응답 필드:
 * - productId, stock, stockStatus
 */
public record ProductStockResponse(
        Long productId,
        int stock,
        StockStatus stockStatus
) {
    public static ProductStockResponse from(ProductStockResult result) {
        return new ProductStockResponse(
                result.productId(),
                result.stock(),
                result.stockStatus()
        );
    }
}
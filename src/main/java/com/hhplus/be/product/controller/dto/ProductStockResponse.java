package com.hhplus.be.product.controller.dto;

import com.hhplus.be.product.service.dto.ProductStockResult;
import lombok.Getter;

/**
 * 상품 재고 조회 Response
 * API: GET /products/{productId}/stock
 */
@Getter
public class ProductStockResponse {
    private final Long productId;
    private final int stock;

    private ProductStockResponse(Long productId, int stock) {
        this.productId = productId;
        this.stock = stock;
    }

    public static ProductStockResponse from(ProductStockResult result) {
        return new ProductStockResponse(
                result.getProductId(),
                result.getStock()
        );
    }
}
package com.hhplus.be.product.service.dto;

import com.hhplus.be.product.domain.Product;
import lombok.Getter;

/**
 * 상품 재고 조회 Result
 * API: GET /products/{productId}/stock
 */
@Getter
public class ProductStockResult {
    private final Long productId;
    private final int stock;

    public ProductStockResult(Product product) {
        this.productId = product.getProduct_id();
        this.stock = product.getStock();
    }
}
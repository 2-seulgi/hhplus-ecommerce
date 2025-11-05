package com.hhplus.be.product.service.dto;

import com.hhplus.be.product.domain.Product;
import com.hhplus.be.product.domain.StockStatus;
import lombok.Getter;

/**
 * 상품 재고 조회 Result
 * API: GET /products/{productId}/stock
 */
@Getter
public class ProductStockResult {
    private final Long productId;
    private final int stock;
    private final StockStatus stockStatus;

    public ProductStockResult(Product product) {
        this.productId = product.getProduct_id();
        this.stock = product.getStock();
        this.stockStatus = product.getStockStatus();
    }
}
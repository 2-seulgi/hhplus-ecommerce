package com.hhplus.be.product.domain.model;

/**
 * 재고 상태
 * API 명세: GET /products/{productId}/stock
 */
public enum StockStatus {
    AVAILABLE,      // 재고 있음
    LOW_STOCK,      // 재고 부족 (임계값 이하)
    OUT_OF_STOCK    // 재고 없음
}
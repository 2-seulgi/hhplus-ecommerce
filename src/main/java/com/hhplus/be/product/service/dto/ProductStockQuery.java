package com.hhplus.be.product.service.dto;

/**
 * 상품 재고 조회 Query
 * API: GET /products/{productId}/stock
 */
public record ProductStockQuery(Long productId) {
}
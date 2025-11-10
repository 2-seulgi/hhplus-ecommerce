package com.hhplus.be.product.service.dto;

/**
 * 상품 상세 조회 Query
 * API: GET /products/{productId}
 */
public record ProductDetailQuery(Long productId) {
}
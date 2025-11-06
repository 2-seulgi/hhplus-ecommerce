package com.hhplus.be.product.service.dto;

import com.hhplus.be.product.domain.Product;

import java.time.Instant;

/**
 * 상품 상세 조회 Result
 * API: GET /products/{productId}
 */
public record ProductDetailResult(
        Long productId,
        String name,
        String description,
        int price,
        int stock,
        Instant createdAt
) {
    public static ProductDetailResult from(Product product) {
        return new ProductDetailResult(
                product.getProduct_id(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getCreatedAt()
        );
    }
}
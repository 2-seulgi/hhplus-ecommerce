package com.hhplus.be.product.service.dto;

import com.hhplus.be.product.domain.Product;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 상품 상세 조회 Result
 * API: GET /products/{productId}
 */
@Getter
public class ProductDetailResult {
    private final Long productId;
    private final String name;
    private final String description;
    private final int price;
    private final int stock;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public ProductDetailResult(Product product) {
        this.productId = product.getProduct_id();
        this.name = product.getName();
        this.description = product.getDescription();
        this.price = product.getPrice();
        this.stock = product.getStock();
        this.createdAt = product.getCreatedAt();
        this.updatedAt = product.getUpdatedAt();
    }
}
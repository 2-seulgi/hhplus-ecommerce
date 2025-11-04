package com.hhplus.be.product.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hhplus.be.product.service.dto.ProductDetailResult;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 상품 상세 조회 Response
 * API: GET /products/{productId}
 */
@Getter
public class ProductDetailResponse {
    private final Long productId;
    private final String name;
    private final String description;
    private final int price;
    private final int stock;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime updatedAt;

    private ProductDetailResponse(Long productId, String name, String description, int price, int stock,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ProductDetailResponse from(ProductDetailResult result) {
        return new ProductDetailResponse(
                result.getProductId(),
                result.getName(),
                result.getDescription(),
                result.getPrice(),
                result.getStock(),
                result.getCreatedAt(),
                result.getUpdatedAt()
        );
    }
}
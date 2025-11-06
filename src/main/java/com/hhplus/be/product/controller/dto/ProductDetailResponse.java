package com.hhplus.be.product.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hhplus.be.product.service.dto.ProductDetailResult;

import java.time.Instant;

/**
 * 상품 상세 조회 Response
 * API: GET /products/{productId}
 *
 * API 명세 응답 필드:
 * - productId, name, description, price, stock, createdAt
 * - updatedAt은 포함하지 않음
 */
public record ProductDetailResponse(
        Long productId,
        String name,
        String description,
        int price,
        int stock,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant createdAt
) {
    public static ProductDetailResponse from(ProductDetailResult result) {
        return new ProductDetailResponse(
                result.productId(),
                result.name(),
                result.description(),
                result.price(),
                result.stock(),
                result.createdAt()
        );
    }
}
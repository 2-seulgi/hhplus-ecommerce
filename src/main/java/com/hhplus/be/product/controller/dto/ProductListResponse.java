package com.hhplus.be.product.controller.dto;

import com.hhplus.be.product.service.dto.ProductListResult;

/**
 * 상품 목록 조회 Response
 * API: GET /products?page=0&size=20
 *
 * API 명세 응답 필드:
 * - productId, name, description, price
 * - 재고(stock), 날짜(createdAt) 정보는 포함하지 않음
 */
public record ProductListResponse(
        Long productId,
        String name,
        String description,
        int price
) {
    public static ProductListResponse from(ProductListResult.ProductItem item) {
        return new ProductListResponse(
                item.productId(),
                item.name(),
                item.description(),
                item.price()
        );
    }
}
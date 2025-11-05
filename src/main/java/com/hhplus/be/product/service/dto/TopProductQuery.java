package com.hhplus.be.product.service.dto;

/**
 * 인기 상품 조회 Query
 * API: GET /products/top?period=3d&limit=5
 */
public record TopProductQuery(
        String period,  // 기간 (예: "3d", "7d", "30d") - 현재는 사용하지 않음
        int limit       // 조회 개수 (default: 5)
) {
}
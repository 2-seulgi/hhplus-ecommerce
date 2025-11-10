package com.hhplus.be.product.service.dto;

/**
 * 상품 목록 조회 Query
 * API: GET /products?page=0&size=20
 */
public record ProductListQuery() {
    // 페이징은 Pageable로 처리
}
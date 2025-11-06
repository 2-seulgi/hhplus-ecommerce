package com.hhplus.be.product.infrastructure;

import com.hhplus.be.product.domain.Product;

import java.util.List;
import java.util.Optional;

/**
 * 상품 Repository 인터페이스
 * API 명세 기반 구현
 */
public interface ProductRepository {
    /**
     * 상품 ID로 조회
     * API: GET /products/{productId}
     * API: GET /products/{productId}/stock
     */
    Optional<Product> findById(Long productId);

    /**
     * 전체 상품 목록 조회
     * API: GET /products?page=0&size=20
     */
    List<Product> findAll();

}

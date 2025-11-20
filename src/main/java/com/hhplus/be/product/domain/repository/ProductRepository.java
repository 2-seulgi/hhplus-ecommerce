package com.hhplus.be.product.domain.repository;

import com.hhplus.be.product.domain.model.Product;

import java.util.List;
import java.util.Optional;

/**
 * 상품 Repository 인터페이스 (Domain Layer)
 * Infrastructure 의존성 없음 (JPA 모름)
 *
 * API 명세 기반 메서드:
 * - findById: GET /products/{productId}
 * - findAll: GET /products?page=0&size=20
 */
public interface ProductRepository {
    /**
     * 상품 ID로 조회
     */
    Optional<Product> findById(Long productId);

    /**
     * 상품 ID로 조회 (Pessimistic Write Lock)
     * 재고 차감 시 동시성 제어를 위해 사용
     */
    Optional<Product> findByIdForUpdate(Long productId);

    /**
     * 전체 상품 목록 조회
     */
    List<Product> findAll();

    /**
     * 상품 저장 (생성/수정)
     */
    Product save(Product product);

    void deleteAll();
}
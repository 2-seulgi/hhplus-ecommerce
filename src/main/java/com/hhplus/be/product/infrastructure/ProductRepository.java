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

    /**
     * 판매량 증가 (주문 완료 시 호출)
     * @param productId 상품 ID
     * @param quantity 판매 수량
     */
    void incrementSalesCount(Long productId, int quantity);

    /**
     * 인기 상품 조회 (최근 판매량 기준 Top N)
     * API: GET /products/top?period=3d&limit=5
     *
     * @param limit 조회 개수
     * @return 판매량 내림차순 정렬된 상품 목록
     */
    List<Product> findTopProducts(int limit);

    /**
     * 판매량 조회
     * @param productId 상품 ID
     * @return 판매량 (없으면 0)
     */
    int getSalesCount(Long productId);
}

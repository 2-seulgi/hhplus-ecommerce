package com.hhplus.be.product.controller;

import com.hhplus.be.product.controller.dto.ProductDetailResponse;
import com.hhplus.be.product.controller.dto.ProductListResponse;
import com.hhplus.be.product.controller.dto.ProductStockResponse;
import com.hhplus.be.product.controller.dto.RankingResponse;
import com.hhplus.be.product.controller.dto.TopProductResponse;
import com.hhplus.be.product.service.ProductService;
import com.hhplus.be.product.service.dto.ProductDetailQuery;
import com.hhplus.be.product.service.dto.ProductListQuery;
import com.hhplus.be.product.service.dto.ProductStockQuery;
import com.hhplus.be.product.service.dto.RankingQuery;
import com.hhplus.be.product.service.dto.TopProductQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 상품 Controller
 * API 명세 기반 구현
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    /**
     * 상품 목록 조회
     * GET /products?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<ProductListResponse>> getProducts(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        var query = new ProductListQuery();
        var result = productService.getProducts(query);

        // 페이징 처리
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), result.products().size());

        var pageContent = result.products().subList(start, end).stream()
                .map(ProductListResponse::from)
                .toList();

        var page = new PageImpl<>(pageContent, pageable, result.products().size());

        return ResponseEntity.ok(page);
    }

    /**
     * 상품 상세 조회
     * GET /products/{productId}
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ProductDetailResponse> getProductDetail(
            @PathVariable Long productId
    ) {
        var query = new ProductDetailQuery(productId);
        var result = productService.getProductDetail(query);
        var response = ProductDetailResponse.from(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 상품 재고 조회
     * GET /products/{productId}/stock
     */
    @GetMapping("/{productId}/stock")
    public ResponseEntity<ProductStockResponse> getProductStock(
            @PathVariable Long productId
    ) {
        var query = new ProductStockQuery(productId);
        var result = productService.getProductStock(query);
        var response = ProductStockResponse.from(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 인기 상품 조회
     * GET /products/top?period=3d&limit=5
     */
    @GetMapping("/top")
    public ResponseEntity<TopProductResponse> getTopProducts(
            @RequestParam(required = false, defaultValue = "3d") String period,
            @RequestParam(required = false, defaultValue = "5") int limit
    ) {
        var query = new TopProductQuery(period, limit);
        var result = productService.getTopProducts(query);
        var response = TopProductResponse.from(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 실시간 상품 랭킹 조회 (Redis Sorted Set 기반)
     * GET /api/v1/products/rankings?period=daily&limit=10
     *
     * 쿼리 파라미터:
     * - period: "daily" (일간), "weekly" (주간), "all" (전체)
     * - limit: 조회 개수 (1~100, 기본값: 10)
     *
     * 기존 /products/top과의 차이:
     * - /top: DB 쿼리 기반, 캐시 10분
     * - /rankings: Redis 실시간, 주문 완료 시 즉시 업데이트
     */
    @GetMapping("/rankings")
    public ResponseEntity<RankingResponse> getRankings(
            @RequestParam(required = false, defaultValue = "daily") String period,
            @RequestParam(required = false, defaultValue = "10") int limit
    ) {
        var query = new RankingQuery(period, limit);
        var result = productService.getRankings(query);
        var response = RankingResponse.from(period, result);
        return ResponseEntity.ok(response);
    }
}
package com.hhplus.be.product.service;

import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.orderitem.domain.OrderItem;
import com.hhplus.be.orderitem.infrastructure.OrderItemRepository;
import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.domain.repository.ProductRepository;
import com.hhplus.be.product.service.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 상품 Service
 * API 명세 기반 구현
 */
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * 상품 목록 조회
     * API: GET /products?page=0&size=20
     */
    public ProductListResult getProducts(ProductListQuery query) {
        List<Product> products = productRepository.findAll();
        return ProductListResult.from(products);
    }

    /**
     * 상품 상세 조회
     * API: GET /products/{productId}
     */
    public ProductDetailResult getProductDetail(ProductDetailQuery query) {
        var product = productRepository.findById(query.productId())
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));
        return ProductDetailResult.from(product);
    }

    /**
     * 상품 재고 조회
     * API: GET /products/{productId}/stock
     */
    public ProductStockResult getProductStock(ProductStockQuery query) {
        var product = productRepository.findById(query.productId())
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));
        return ProductStockResult.from(product);
    }

    /**
     * 인기 상품 조회 (최근 N일간 CONFIRMED 주문 기준)
     * API: GET /products/top?period=3d&limit=5
     *
     * 실제 판매량 = ORDER 테이블의 CONFIRMED 상태 주문 기준으로 집계
     */
    public TopProductResult getTopProducts(TopProductQuery query) {
        // 1. 최근 N일 계산 (기본 3일)
        int days = parsePeriod(query.period());
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        // 2. CONFIRMED 주문의 상품별 판매량 집계
        Map<Long, Integer> salesByProduct = orderItemRepository.countSalesByProductSince(since);

        // 3. 판매량 Top N 상품 조회
        List<TopProductResult.ProductItem> items = salesByProduct.entrySet().stream()
                // 판매량 내림차순 정렬
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                // 상위 N개 선택
                .limit(query.limit())
                // Product 조회 + DTO 변환
                .map(entry -> {
                    Long productId = entry.getKey();
                    int salesCount = entry.getValue();
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));
                    return TopProductResult.ProductItem.from(product, salesCount);
                })
                .toList();

        return new TopProductResult(items);
    }

    /**
     * period 파라미터 파싱 (예: "3d" -> 3)
     * 기본값: 3일
     */
    private int parsePeriod(String period) {
        if (period == null || period.isEmpty()) {
            return 3;
        }
        try {
            // "3d" -> 3
            return Integer.parseInt(period.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    /**
     * 여러 상품의 재고 일괄 차감 (UseCase용)
     */
    public void decreaseStocks(List<OrderItem> orderItems) {
        for (OrderItem orderItem : orderItems) {
            Product product = productRepository.findById(orderItem.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));
            product.decreaseStock(orderItem.getQuantity());
            productRepository.save(product);  // 변경사항 저장
        }
    }

    /**
     * 여러 상품의 재고 일괄 복원 (UseCase용)
     */
    public void increaseStocks(List<OrderItem> orderItems) {
        for (OrderItem item : orderItems) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));

            product.increaseStock(item.getQuantity());
            productRepository.save(product);  // 변경사항 저장
        }
    }


}
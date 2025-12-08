package com.hhplus.be.product.service;

import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.orderitem.domain.repository.OrderItemRepository;
import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.domain.repository.ProductRepository;
import com.hhplus.be.product.service.dto.*;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;  // ✅ readOnly 지원
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Clock;
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
    private final ProductRankingService productRankingService;
    private final Clock clock;

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
     * 한 번의 조인 쿼리로 상품 정보와 판매량을 함께 조회
     * Redis 캐시 적용 (TTL 10분)
     */
    @Cacheable(value = "topProducts", key = "#query.period() + ':' + #query.limit()")
    @Transactional(readOnly = true)
    public TopProductResult getTopProducts(TopProductQuery query) {
        // 1. 최근 N일 계산 (기본 3일)
        int days = parsePeriod(query.period());
        Instant since = clock.instant().minus(days, ChronoUnit.DAYS);

        // 2. 한 번의 쿼리로 상품 정보 + 판매량 조회 (이미 판매량 내림차순 정렬됨)
        List<TopProductResult.ProductItem> items = orderItemRepository
                .findTopSellingProductsSince(since)
                .stream()
                .limit(query.limit())
                .map(result -> new TopProductResult.ProductItem(
                        result.productId(),
                        result.name(),
                        result.price(),
                        result.getSalesCount()
                ))
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
     * Pessimistic Write Lock을 사용하여 동시성 제어
     *
     * 데드락 방지를 위해 productId 오름차순으로 정렬하여 락 획득
     * 예: [3,1,5] → [1,3,5] 순서로 락 획득
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));

        product.decreaseStock(quantity);
        productRepository.save(product);
    }

    /**
     * 개별 상품 재고 복원 (분산락 내에서만 호출)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));

        product.increaseStock(quantity);
        productRepository.save(product);
    }

    /**
     * 실시간 상품 랭킹 조회 (Redis Sorted Set 기반)
     * API: GET /api/v1/products/rankings?period=daily&limit=10
     *
     * 처리 흐름:
     * 1. Redis에서 상품 ID + 판매량 조회 (O(log N))
     * 2. DB에서 상품 상세 정보 조회 (IN 쿼리, 1회)
     * 3. 랭킹 순서 유지하며 결합
     *
     * @param query 랭킹 조회 쿼리 (period: daily/weekly/all, limit: 1~100)
     * @return 상품 정보 + 판매량 + 순위
     */
    @Transactional(readOnly = true)
    public RankingResult getRankings(RankingQuery query) {
        // 1. Redis에서 Top N 상품 조회 (productId + salesCount)
        List<ProductRankingService.RankingResult> redisRankings = switch (query.period()) {
            case "daily" -> productRankingService.getTopProductsDaily(query.limit());
            case "weekly" -> productRankingService.getTopProductsWeekly(query.limit());
            case "all" -> productRankingService.getTopProductsAll(query.limit());
            default -> throw new IllegalArgumentException("Invalid period: " + query.period());
        };

        // 2. 상품 ID 추출
        List<Long> productIds = redisRankings.stream()
                .map(ProductRankingService.RankingResult::productId)
                .toList();

        // 3. DB에서 상품 상세 정보 일괄 조회 (IN 쿼리 1회)
        List<Product> products = productRepository.findAllById(productIds);

        // 4. productId -> Product 맵 생성 (O(1) 조회)
        Map<Long, Product> productMap = products.stream()
                .collect(java.util.stream.Collectors.toMap(
                        Product::getId,
                        p -> p
                ));

        // 5. Redis 순서 유지하며 랭킹 결과 생성
        List<RankingResult.RankingItem> rankingItems = new java.util.ArrayList<>();
        int rank = 1;
        for (ProductRankingService.RankingResult redisRanking : redisRankings) {
            Product product = productMap.get(redisRanking.productId());

            // 상품이 삭제된 경우 스킵
            if (product == null) {
                continue;
            }

            rankingItems.add(RankingResult.RankingItem.builder()
                    .rank(rank++)
                    .productId(product.getId())
                    .productName(product.getName())
                    .price(product.getPrice())
                    .salesCount(redisRanking.salesCount())
                    .build());
        }

        return RankingResult.builder()
                .rankings(rankingItems)
                .build();
    }

}
package com.hhplus.be.product.infrastructure;

import com.hhplus.be.product.domain.Product;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    /**
     * 판매량 캐시 (InMemory 구현)
     *
     * 실무에서는 다음 방식 권장:
     * - Redis Sorted Set (실시간 집계, 빠른 조회)
     * - 별도 집계 테이블 (ProductSalesStats) + 스케줄러
     *
     * Key: 상품ID, Value: 누적 판매량
     */
    private final Map<Long, Integer> salesCountCache = new ConcurrentHashMap<>();

    public InMemoryProductRepository() {
        initializeData();
        initializeSalesData();
    }

    private void initializeData() {
        // 초기 상품 데이터 (10개)
        saveProduct(Product.create("무선 이어폰", "고음질 블루투스 이어폰", 89000, 100));
        saveProduct(Product.create("스마트워치", "건강 관리 스마트워치", 250000, 50));
        saveProduct(Product.create("노트북 파우치", "15인치 노트북용 파우치", 25000, 200));
        saveProduct(Product.create("USB-C 케이블", "고속 충전 케이블 1m", 15000, 500));
        saveProduct(Product.create("휴대폰 거치대", "차량용 스마트폰 거치대", 12000, 300));
        saveProduct(Product.create("블루투스 스피커", "방수 휴대용 스피커", 45000, 80));
        saveProduct(Product.create("보조배터리", "20000mAh 고속 충전", 35000, 150));
        saveProduct(Product.create("무선 충전기", "15W 고속 무선 충전", 28000, 120));
        saveProduct(Product.create("마우스 패드", "게이밍 대형 마우스 패드", 18000, 250));
        saveProduct(Product.create("키보드", "기계식 RGB 키보드", 120000, 60));
    }

    private void initializeSalesData() {
        // 테스트용 초기 판매량 데이터
        salesCountCache.put(1L, 150);  // 무선 이어폰
        salesCountCache.put(2L, 120);  // 스마트워치
        salesCountCache.put(6L, 95);   // 블루투스 스피커
        salesCountCache.put(4L, 80);   // USB-C 케이블
        salesCountCache.put(7L, 65);   // 보조배터리
    }

    private void saveProduct(Product product) {
        Long id = idGenerator.getAndIncrement();
        product.assignId(id);
        store.put(id, product);
    }

    @Override
    public Optional<Product> findById(Long productId) {
        return Optional.ofNullable(store.get(productId));
    }

    @Override
    public List<Product> findAll() {
        return store.values().stream().toList();
    }

    /**
     * 판매량 증가 (주문 완료 시 호출)
     * @param productId 상품 ID
     * @param quantity 판매 수량
     */
    @Override
    public void incrementSalesCount(Long productId, int quantity) {
        // merge: Key가 없으면 quantity를 저장, 있으면 기존 값 + quantity
        // Integer::sum은 (oldValue, newValue) -> oldValue + newValue와 동일
        salesCountCache.merge(productId, quantity, Integer::sum);
    }

    /**
     * 인기 상품 조회 (판매량 기준 Top N)
     * @param limit 조회 개수
     * @return 판매량 내림차순 정렬된 상품 목록
     */
    @Override
    public List<Product> findTopProducts(int limit) {
        return salesCountCache.entrySet().stream()
                // 1. Entry를 판매량(Value) 기준으로 정렬
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                // 2. 상위 N개만 선택
                .limit(limit)
                // 3. Entry -> Product 변환 (상품ID로 조회)
                .map(entry -> findById(entry.getKey()).orElseThrow())
                // 4. List로 변환
                .toList();
    }

    /**
     * 판매량 조회
     * @param productId 상품 ID
     * @return 판매량 (없으면 0)
     */
    @Override
    public int getSalesCount(Long productId) {
        return salesCountCache.getOrDefault(productId, 0);
    }
}

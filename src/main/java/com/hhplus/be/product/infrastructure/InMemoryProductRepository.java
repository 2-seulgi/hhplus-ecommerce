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

    public InMemoryProductRepository() {
        initializeData();
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
}

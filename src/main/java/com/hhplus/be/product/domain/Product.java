package com.hhplus.be.product.domain;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.InvalidInputException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long product_id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stock;

    @Version
    private int version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // 상품은 이미 등록되어 있다고 가정 (테스트/초기 데이터 생성용)
    private Product(String name, String description, int price, int stock) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.version = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public static Product create(String name, String description, int price, int stock) {
        return new Product(name, description, price, stock);
    }

    // 재고 차감 (결제 시)
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new InvalidInputException("차감 수량은 양수여야 합니다");
        }
        if (this.stock < quantity) {
            throw new BusinessException("재고가 부족합니다", "OUT_OF_STOCK");
        }
        this.stock -= quantity;
        this.updatedAt = Instant.now();
    }

    // 재고 복원 (주문 취소 시)
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new InvalidInputException("복원 수량은 양수여야 합니다");
        }
        this.stock += quantity;
        this.updatedAt = Instant.now();
    }

    /**
     * 재고 상태 계산
     * API 명세: GET /products/{productId}/stock
     */
    public StockStatus getStockStatus() {
        if (this.stock == 0) {
            return StockStatus.OUT_OF_STOCK;
        } else if (this.stock < 10) {
            return StockStatus.LOW_STOCK;
        } else {
            return StockStatus.AVAILABLE;
        }
    }

    /**
     * ID 할당 (Repository 전용 메서드)
     * JPA 도입 시 제거 예정
     *
     * WARNING: 비즈니스 로직에서 호출 금지!
     * Repository 구현체에서만 사용해야 합니다.
     */
    public void assignId(Long id) {
        if (this.product_id != null) {
            throw new IllegalStateException("ID는 한 번만 할당할 수 있습니다");
        }
        this.product_id = id;
    }
}

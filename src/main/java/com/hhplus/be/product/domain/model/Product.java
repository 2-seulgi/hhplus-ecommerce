package com.hhplus.be.product.domain.model;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.InvalidInputException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 상품 Domain Model (순수 비즈니스 객체)
 * Infrastructure(JPA) 의존성 없음
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
    private Long id;
    private String name;
    private String description;
    private int price;
    private int stock;
    private int version;
    private Instant createdAt;
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

    public static Product reconstruct(Long id, String name, String description, int price, int stock,
                                      int version, Instant createdAt, Instant updatedAt) {
        return new Product(id, name, description, price, stock, version, createdAt, updatedAt);
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

}

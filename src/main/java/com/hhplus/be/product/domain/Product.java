package com.hhplus.be.product.domain;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.InvalidInputException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 상품은 이미 등록되어 있다고 가정 (테스트/초기 데이터 생성용)
    private Product(String name, String description, int price, int stock) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.version = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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
        this.updatedAt = LocalDateTime.now();
    }

    // 재고 복원 (주문 취소 시)
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new InvalidInputException("복원 수량은 양수여야 합니다");
        }
        this.stock += quantity;
        this.updatedAt = LocalDateTime.now();
    }




}

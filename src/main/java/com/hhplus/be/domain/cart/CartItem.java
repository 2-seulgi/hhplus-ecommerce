package com.hhplus.be.domain.cart;

import com.hhplus.be.common.exception.InvalidInputException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "cart_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private CartItem(Long userId, Long productId, int quantity) {
        validateQuantity(quantity);
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public static CartItem create(Long userId, Long productId, int quantity) {
        return new CartItem(userId, productId, quantity);
    }

    public void changeQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new InvalidInputException("수량은 1 이상이어야 합니다");
        }
    }
}
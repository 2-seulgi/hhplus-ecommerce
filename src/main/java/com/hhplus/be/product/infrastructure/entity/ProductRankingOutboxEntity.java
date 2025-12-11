package com.hhplus.be.product.infrastructure.entity;

import com.hhplus.be.order.domain.model.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 상품 랭킹 Outbox JPA Entity
 */
@Entity
@Table(name = "product_ranking_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ProductRankingOutboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant processedAt;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant updatedAt;
}
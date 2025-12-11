package com.hhplus.be.product.domain.model;

import com.hhplus.be.order.domain.model.OutboxStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 상품 랭킹 업데이트 Outbox
 *
 * 역할:
 * - 랭킹 업데이트 이벤트 처리 이력 저장
 * - 실패 시 재처리를 위한 데이터 보관
 * - 상태 추적 (PENDING, SUCCESS, FAILED)
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductRankingOutbox {
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private OutboxStatus status;
    private Integer retryCount;
    private Instant createdAt;
    private Instant processedAt;
    private String errorMessage;
    private Instant updatedAt;

    /**
     * 새 Outbox 레코드 생성 (PENDING 상태)
     */
    public static ProductRankingOutbox create(
            Long orderId,
            Long productId,
            Integer quantity,
            Instant now
    ) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("수량은 양수여야 합니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("생성 시각은 필수입니다");
        }

        return ProductRankingOutbox.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(quantity)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 처리 성공으로 상태 변경
     */
    public void markAsSuccess(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("처리 시각은 필수입니다");
        }

        this.status = OutboxStatus.SUCCESS;
        this.processedAt = now;
        this.updatedAt = now;
        this.errorMessage = null;
    }

    /**
     * 처리 실패로 상태 변경
     */
    public void markAsFailed(String errorMessage, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("처리 시각은 필수입니다");
        }

        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = now;
    }

    /**
     * 재시도 횟수 증가
     */
    public void incrementRetry(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("갱신 시각은 필수입니다");
        }

        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
        this.updatedAt = now;
    }

    /**
     * 재처리 시작 시 PENDING으로 변경
     */
    public void markAsPending(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("갱신 시각은 필수입니다");
        }

        this.status = OutboxStatus.PENDING;
        this.errorMessage = null;
        this.updatedAt = now;
    }
}
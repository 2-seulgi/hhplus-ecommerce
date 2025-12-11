package com.hhplus.be.product.domain.repository;

import com.hhplus.be.order.domain.model.OutboxStatus;
import com.hhplus.be.product.domain.model.ProductRankingOutbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 상품 랭킹 Outbox 저장소 인터페이스
 */
public interface ProductRankingOutboxRepository {
    /**
     * Outbox 저장
     */
    ProductRankingOutbox save(ProductRankingOutbox outbox);

    /**
     * ID로 조회
     */
    Optional<ProductRankingOutbox> findById(Long id);

    /**
     * 특정 상태의 Outbox 조회 (재시도 대상)
     *
     * @param status 조회할 상태
     * @param maxRetryCount 최대 재시도 횟수
     * @param before 생성 시각 기준 (이 시각 이전 레코드만)
     * @return Outbox 목록
     */
    List<ProductRankingOutbox> findByStatusAndRetryCountLessThanAndCreatedAtBefore(
            OutboxStatus status,
            int maxRetryCount,
            Instant before
    );
}
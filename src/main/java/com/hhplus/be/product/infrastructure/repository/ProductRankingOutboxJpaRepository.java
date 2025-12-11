package com.hhplus.be.product.infrastructure.repository;

import com.hhplus.be.order.domain.model.OutboxStatus;
import com.hhplus.be.product.infrastructure.entity.ProductRankingOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * 상품 랭킹 Outbox JPA Repository
 */
public interface ProductRankingOutboxJpaRepository extends JpaRepository<ProductRankingOutboxEntity, Long> {
    /**
     * 특정 상태의 Outbox 조회 (재시도 대상)
     */
    List<ProductRankingOutboxEntity> findByStatusAndRetryCountLessThanAndCreatedAtBefore(
            OutboxStatus status,
            int maxRetryCount,
            Instant before
    );
}
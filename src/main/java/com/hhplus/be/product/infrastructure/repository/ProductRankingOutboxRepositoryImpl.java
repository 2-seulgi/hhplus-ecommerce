package com.hhplus.be.product.infrastructure.repository;

import com.hhplus.be.order.domain.model.OutboxStatus;
import com.hhplus.be.product.domain.model.ProductRankingOutbox;
import com.hhplus.be.product.domain.repository.ProductRankingOutboxRepository;
import com.hhplus.be.product.infrastructure.mapper.ProductRankingOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 상품 랭킹 Outbox Repository 구현
 */
@Repository
@RequiredArgsConstructor
public class ProductRankingOutboxRepositoryImpl implements ProductRankingOutboxRepository {
    private final ProductRankingOutboxJpaRepository jpaRepository;
    private final ProductRankingOutboxMapper mapper;

    @Override
    public ProductRankingOutbox save(ProductRankingOutbox outbox) {
        var entity = mapper.toEntity(outbox);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ProductRankingOutbox> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<ProductRankingOutbox> findByStatusAndRetryCountLessThanAndCreatedAtBefore(
            OutboxStatus status,
            int maxRetryCount,
            Instant before
    ) {
        return jpaRepository.findByStatusAndRetryCountLessThanAndCreatedAtBefore(status, maxRetryCount, before)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
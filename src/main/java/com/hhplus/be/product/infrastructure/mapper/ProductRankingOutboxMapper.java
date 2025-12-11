package com.hhplus.be.product.infrastructure.mapper;

import com.hhplus.be.product.domain.model.ProductRankingOutbox;
import com.hhplus.be.product.infrastructure.entity.ProductRankingOutboxEntity;
import org.springframework.stereotype.Component;

/**
 * 상품 랭킹 Outbox Mapper
 */
@Component
public class ProductRankingOutboxMapper {
    /**
     * Domain → Entity
     */
    public ProductRankingOutboxEntity toEntity(ProductRankingOutbox domain) {
        return new ProductRankingOutboxEntity(
                domain.getId(),
                domain.getOrderId(),
                domain.getProductId(),
                domain.getQuantity(),
                domain.getStatus(),
                domain.getRetryCount(),
                domain.getCreatedAt(),
                domain.getProcessedAt(),
                domain.getErrorMessage(),
                domain.getUpdatedAt()
        );
    }

    /**
     * Entity → Domain
     */
    public ProductRankingOutbox toDomain(ProductRankingOutboxEntity entity) {
        return ProductRankingOutbox.builder()
                .id(entity.getId())
                .orderId(entity.getOrderId())
                .productId(entity.getProductId())
                .quantity(entity.getQuantity())
                .status(entity.getStatus())
                .retryCount(entity.getRetryCount())
                .createdAt(entity.getCreatedAt())
                .processedAt(entity.getProcessedAt())
                .errorMessage(entity.getErrorMessage())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
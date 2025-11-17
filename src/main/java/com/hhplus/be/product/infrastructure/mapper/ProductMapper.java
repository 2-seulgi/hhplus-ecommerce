package com.hhplus.be.product.infrastructure.mapper;

import com.hhplus.be.product.infrastructure.entity.Product;
import org.springframework.stereotype.Component;

/**
 * Product Domain ↔ ProductJpaEntity 변환 Mapper
 */
@Component
public class ProductMapper {

    /**
     * JPA Entity → Domain Model
     */
    public com.hhplus.be.product.domain.model.Product toDomain(Product entity) {
        if (entity == null) {
            return null;
        }

        return com.hhplus.be.product.domain.model.Product.reconstruct(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getStock(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * Domain Model → JPA Entity
     */
    public Product toEntity(com.hhplus.be.product.domain.model.Product domain) {
        if (domain == null) {
            return null;
        }

        return new Product(
                domain.getId(),
                domain.getName(),
                domain.getDescription(),
                domain.getPrice(),
                domain.getStock(),
                domain.getVersion(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
package com.hhplus.be.order.infrastructure.mapper;

import org.springframework.stereotype.Component;

@Component
public class OrderDataPlatformOutboxMapper {

    public com.hhplus.be.order.domain.model.OrderDataPlatformOutbox toDomain(
            com.hhplus.be.order.infrastructure.entity.OrderDataPlatformOutbox entity) {
        if (entity == null) {
            return null;
        }
        return com.hhplus.be.order.domain.model.OrderDataPlatformOutbox.reconstruct(
                entity.getId(),
                entity.getOrderId(),
                entity.getUserId(),
                entity.getOrderData(),
                entity.getStatus(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getSentAt(),
                entity.getErrorMessage(),
                entity.getUpdatedAt()
        );
    }

    public com.hhplus.be.order.infrastructure.entity.OrderDataPlatformOutbox toEntity(
            com.hhplus.be.order.domain.model.OrderDataPlatformOutbox domain) {
        if (domain == null) {
            return null;
        }
        return new com.hhplus.be.order.infrastructure.entity.OrderDataPlatformOutbox(
                domain.getId(),
                domain.getOrderId(),
                domain.getUserId(),
                domain.getOrderData(),
                domain.getStatus(),
                domain.getRetryCount(),
                domain.getCreatedAt(),
                domain.getSentAt(),
                domain.getErrorMessage(),
                domain.getUpdatedAt()
        );
    }
}
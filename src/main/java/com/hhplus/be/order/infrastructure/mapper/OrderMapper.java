package com.hhplus.be.order.infrastructure.mapper;

import com.hhplus.be.order.infrastructure.entity.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public com.hhplus.be.order.domain.model.Order toDomain(Order entity) {
        if (entity == null) {
            return null;
        }
        return com.hhplus.be.order.domain.model.Order.reconstruct(
                entity.getId(),
                entity.getUserId(),
                entity.getStatus(),
                entity.getTotalAmount(),
                entity.getFinalAmount(),
                entity.getExpiresAt(),
                entity.getPaidAt(),
                entity.getCanceledAt(),
                entity.getRefundedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public Order toEntity(com.hhplus.be.order.domain.model.Order domain) {
        if (domain == null) {
            return null;
        }
        return new Order(
                domain.getId(),
                domain.getUserId(),
                domain.getStatus(),
                domain.getTotalAmount(),
                domain.getFinalAmount(),
                domain.getExpiresAt(),
                domain.getPaidAt(),
                domain.getCanceledAt(),
                domain.getRefundedAt(),
                domain.getCreatedAt(),
                domain.getUpdatedAt(),
                domain.getVersion()
        );
    }
}

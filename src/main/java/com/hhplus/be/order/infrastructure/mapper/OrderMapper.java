package com.hhplus.be.order.infrastructure.mapper;

import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.infrastructure.entity.OrderJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public Order toDomain(OrderJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Order.reconstruct(
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
                entity.getUpdatedAt()
        );
    }

    public OrderJpaEntity toEntity(Order domain) {
        if (domain == null) {
            return null;
        }
        return new OrderJpaEntity(
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
                domain.getUpdatedAt()
        );
    }
}

package com.hhplus.be.orderitem.infrastructure.mapper;

import com.hhplus.be.orderitem.domain.model.OrderItem;
import com.hhplus.be.orderitem.infrastructure.entity.OrderItemJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class OrderItemMapper {
    public OrderItem toDomain(OrderItemJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return OrderItem.reconstruct(
                entity.getId(),
                entity.getOrderId(),
                entity.getProductId(),
                entity.getProductName(),
                entity.getUnitPrice(),
                entity.getQuantity(),
                entity.getCreatedAt()
        );
    }

    public OrderItemJpaEntity toEntity(OrderItem domain) {
        if (domain == null) {
            return null;
        }
        return new OrderItemJpaEntity(
                domain.getId(),
                domain.getOrderId(),
                domain.getProductId(),
                domain.getProductName(),
                domain.getUnitPrice(),
                domain.getQuantity(),
                domain.getCreatedAt()
        );
    }
}

package com.hhplus.be.orderitem.infrastructure.mapper;

import com.hhplus.be.orderitem.infrastructure.entity.OrderItem;
import org.springframework.stereotype.Component;

@Component
public class OrderItemMapper {
    public com.hhplus.be.orderitem.domain.model.OrderItem toDomain(OrderItem entity) {
        if (entity == null) {
            return null;
        }
        return com.hhplus.be.orderitem.domain.model.OrderItem.reconstruct(
                entity.getId(),
                entity.getOrderId(),
                entity.getProductId(),
                entity.getProductName(),
                entity.getUnitPrice(),
                entity.getQuantity(),
                entity.getCreatedAt()
        );
    }

    public OrderItem toEntity(com.hhplus.be.orderitem.domain.model.OrderItem domain) {
        if (domain == null) {
            return null;
        }
        return new OrderItem(
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

package com.hhplus.be.orderdiscount.infrastructure.mapper;

import com.hhplus.be.orderdiscount.domain.OrderDiscount;
import com.hhplus.be.orderdiscount.infrastructure.entity.OrderDiscountJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class OrderDiscountMapper {
    public OrderDiscount toDomain(OrderDiscountJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return OrderDiscount.reconstruct(
                entity.getId(),
                entity.getOrderId(),
                entity.getUserCouponId(),
                entity.getDiscountType(),
                entity.getDiscountValue(),
                entity.getDiscountAmount(),
                entity.getCreatedAt()
        );
    }

    public OrderDiscountJpaEntity toEntity(OrderDiscount domain) {
        if (domain == null) {
            return null;
        }
        return new OrderDiscountJpaEntity(
                domain.getId(),
                domain.getOrderId(),
                domain.getUserCouponId(),
                domain.getDiscountType(),
                domain.getDiscountValue(),
                domain.getDiscountAmount(),
                domain.getCreatedAt()
        );
    }
}
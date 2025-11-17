package com.hhplus.be.orderdiscount.infrastructure.mapper;

import com.hhplus.be.orderdiscount.infrastructure.entity.OrderDiscount;
import org.springframework.stereotype.Component;

@Component
public class OrderDiscountMapper {
    public com.hhplus.be.orderdiscount.domain.OrderDiscount toDomain(OrderDiscount entity) {
        if (entity == null) {
            return null;
        }
        return com.hhplus.be.orderdiscount.domain.OrderDiscount.reconstruct(
                entity.getId(),
                entity.getOrderId(),
                entity.getUserCouponId(),
                entity.getDiscountType(),
                entity.getDiscountValue(),
                entity.getDiscountAmount(),
                entity.getCreatedAt()
        );
    }

    public OrderDiscount toEntity(com.hhplus.be.orderdiscount.domain.OrderDiscount domain) {
        if (domain == null) {
            return null;
        }
        return new OrderDiscount(
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
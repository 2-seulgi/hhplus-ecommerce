package com.hhplus.be.coupon.infrastructure.mapper;

import com.hhplus.be.coupon.domain.model.Coupon;
import com.hhplus.be.coupon.infrastructure.entity.CouponJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class CouponMapper {
    public Coupon toDomain(CouponJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Coupon.reconstruct(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDiscountType(),
                entity.getDiscountValue(),
                entity.getTotalQuantity(),
                entity.getIssuedQuantity(),
                entity.getVersion(),
                entity.getIssueStartAt(),
                entity.getIssueEndAt(),
                entity.getUseStartAt(),
                entity.getUseEndAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public CouponJpaEntity toEntity(Coupon domain) {
        if (domain == null) {
            return null;
        }
        return new CouponJpaEntity(
                domain.getId(),
                domain.getCode(),
                domain.getName(),
                domain.getDiscountType(),
                domain.getDiscountValue(),
                domain.getTotalQuantity(),
                domain.getIssuedQuantity(),
                domain.getVersion(),
                domain.getIssueStartAt(),
                domain.getIssueEndAt(),
                domain.getUseStartAt(),
                domain.getUseEndAt(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
package com.hhplus.be.usercoupon.infrastructure.mapper;

import com.hhplus.be.usercoupon.infrastructure.entity.UserCoupon;
import org.springframework.stereotype.Component;

@Component
public class UserCouponMapper {
    public com.hhplus.be.usercoupon.domain.model.UserCoupon toDomain(UserCoupon entity) {
        if (entity == null) {
            return null;
        }
        return com.hhplus.be.usercoupon.domain.model.UserCoupon.reconstruct(
                entity.getId(),
                entity.getUserId(),
                entity.getCouponId(),
                entity.isUsed(),
                entity.getUsedAt(),
                entity.getIssuedAt()
        );
    }

    public UserCoupon toEntity(com.hhplus.be.usercoupon.domain.model.UserCoupon domain) {
        if (domain == null) {
            return null;
        }
        return new UserCoupon(
                domain.getId(),
                domain.getUserId(),
                domain.getCouponId(),
                domain.isUsed(),
                domain.getUsedAt(),
                domain.getIssuedAt()
        );
    }
}
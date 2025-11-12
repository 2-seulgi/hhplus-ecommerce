package com.hhplus.be.usercoupon.infrastructure.mapper;

import com.hhplus.be.usercoupon.domain.model.UserCoupon;
import com.hhplus.be.usercoupon.infrastructure.entity.UserCouponJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UserCouponMapper {
    public UserCoupon toDomain(UserCouponJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return UserCoupon.reconstruct(
                entity.getId(),
                entity.getUserId(),
                entity.getCouponId(),
                entity.isUsed(),
                entity.getUsedAt(),
                entity.getIssuedAt()
        );
    }

    public UserCouponJpaEntity toEntity(UserCoupon domain) {
        if (domain == null) {
            return null;
        }
        return new UserCouponJpaEntity(
                domain.getId(),
                domain.getUserId(),
                domain.getCouponId(),
                domain.isUsed(),
                domain.getUsedAt(),
                domain.getIssuedAt()
        );
    }
}
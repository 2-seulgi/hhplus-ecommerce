package com.hhplus.be.usercoupon.infrastructure.repository;

import com.hhplus.be.usercoupon.infrastructure.entity.UserCouponJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponJpaEntity, Long> {
    List<UserCouponJpaEntity> findByUserId(Long userId);
    List<UserCouponJpaEntity> findByUserIdAndUsed(Long userId, boolean used);
    Optional<UserCouponJpaEntity> findByUserIdAndCouponId(Long userId, Long couponId);
}
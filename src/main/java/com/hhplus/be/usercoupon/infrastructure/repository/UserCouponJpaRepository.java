package com.hhplus.be.usercoupon.infrastructure.repository;

import com.hhplus.be.usercoupon.infrastructure.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {
    List<UserCoupon> findByUserId(Long userId);
    List<UserCoupon> findByUserIdAndUsed(Long userId, boolean used);
    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);
}
package com.hhplus.be.usercoupon.domain.repository;

import com.hhplus.be.usercoupon.domain.model.UserCoupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);
    Optional<UserCoupon> findById(Long id);
    List<UserCoupon> findByUserId(Long userId);
    List<UserCoupon> findByUserIdAndUsed(Long userId, boolean used);
    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);
    List<UserCoupon> findAll();
}
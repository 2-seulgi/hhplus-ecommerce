package com.hhplus.be.coupon.infrastructure;

import com.hhplus.be.coupon.domain.Coupon;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    Coupon save(Coupon coupon);
    Optional<Coupon> findById(Long id);
    Optional<Coupon> findByCode(String code);
    List<Coupon> findAll();
}
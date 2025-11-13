package com.hhplus.be.coupon.domain.repository;

import com.hhplus.be.coupon.domain.model.Coupon;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    Coupon save(Coupon coupon);
    Optional<Coupon> findById(Long id);
    Optional<Coupon> findByCode(String code);
    List<Coupon> findAll();

    void deleteAll();
}
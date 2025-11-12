package com.hhplus.be.coupon.infrastructure.repository;

import com.hhplus.be.coupon.infrastructure.entity.CouponJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<CouponJpaEntity, Long> {
    Optional<CouponJpaEntity> findByCode(String code);
}
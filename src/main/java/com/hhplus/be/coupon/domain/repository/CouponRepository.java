package com.hhplus.be.coupon.domain.repository;

import com.hhplus.be.coupon.domain.model.Coupon;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    Coupon save(Coupon coupon);
    Optional<Coupon> findById(Long id);
    Optional<Coupon> findByIdForUpdate(Long id);  // 비관적 락
    Optional<Coupon> findByCode(String code);
    List<Coupon> findAll();

    /**
     * 발급 기간 중인 쿠폰 조회 (Consumer가 처리할 쿠폰 목록)
     * issueStartAt <= now <= issueEndAt
     */
    List<Coupon> findAllByIssuePeriod(Instant now);

    void deleteAll();

    void deleteAllInBatch();

}
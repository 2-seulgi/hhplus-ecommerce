package com.hhplus.be.coupon.infrastructure.repository;

import com.hhplus.be.coupon.domain.model.Coupon;
import com.hhplus.be.coupon.infrastructure.mapper.CouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements com.hhplus.be.coupon.domain.repository.CouponRepository {
    private final CouponJpaRepository couponJpaRepository;
    private final CouponMapper couponMapper;

    @Override
    public Coupon save(Coupon coupon) {
        var entity = couponMapper.toEntity(coupon);
        var savedEntity = couponJpaRepository.save(entity);
        return couponMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return couponJpaRepository.findById(id)
                .map(couponMapper::toDomain);
    }

    @Override
    public Optional<Coupon> findByIdForUpdate(Long id) {
        return couponJpaRepository.findByIdForUpdate(id)
                .map(couponMapper::toDomain);
    }

    @Override
    public Optional<Coupon> findByCode(String code) {
        return couponJpaRepository.findByCode(code)
                .map(couponMapper::toDomain);
    }

    @Override
    public List<Coupon> findAll() {
        return couponJpaRepository.findAll().stream()
                .map(couponMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteAll() {
        couponJpaRepository.deleteAll();
    }
}
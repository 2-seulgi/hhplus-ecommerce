package com.hhplus.be.usercoupon.infrastructure.repository;

import com.hhplus.be.usercoupon.domain.model.UserCoupon;
import com.hhplus.be.usercoupon.domain.repository.UserCouponRepository;
import com.hhplus.be.usercoupon.infrastructure.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserCouponRepositoryImpl implements UserCouponRepository {
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final UserCouponMapper userCouponMapper;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        var entity = userCouponMapper.toEntity(userCoupon);
        var savedEntity = userCouponJpaRepository.save(entity);
        return userCouponMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return userCouponJpaRepository.findById(id)
                .map(userCouponMapper::toDomain);
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return userCouponJpaRepository.findByUserId(userId).stream()
                .map(userCouponMapper::toDomain)
                .toList();
    }

    @Override
    public List<UserCoupon> findByUserIdAndUsed(Long userId, boolean used) {
        return userCouponJpaRepository.findByUserIdAndUsed(userId, used).stream()
                .map(userCouponMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponJpaRepository.findByUserIdAndCouponId(userId, couponId)
                .map(userCouponMapper::toDomain);
    }

    @Override
    public List<UserCoupon> findAll() {
        return userCouponJpaRepository.findAll().stream()
                .map(userCouponMapper::toDomain)
                .toList();
    }
}
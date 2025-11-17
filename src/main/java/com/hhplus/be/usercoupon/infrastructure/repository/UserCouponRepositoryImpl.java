package com.hhplus.be.usercoupon.infrastructure.repository;

import com.hhplus.be.usercoupon.domain.model.UserCoupon;
import com.hhplus.be.usercoupon.infrastructure.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserCouponRepositoryImpl implements com.hhplus.be.usercoupon.domain.repository.UserCouponRepository {
    private final UserCouponRepository userCouponRepository;
    private final UserCouponMapper userCouponMapper;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        var entity = userCouponMapper.toEntity(userCoupon);
        var savedEntity = userCouponRepository.save(entity);
        return userCouponMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return userCouponRepository.findById(id)
                .map(userCouponMapper::toDomain);
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return userCouponRepository.findByUserId(userId).stream()
                .map(userCouponMapper::toDomain)
                .toList();
    }

    @Override
    public List<UserCoupon> findByUserIdAndUsed(Long userId, boolean used) {
        return userCouponRepository.findByUserIdAndUsed(userId, used).stream()
                .map(userCouponMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponRepository.findByUserIdAndCouponId(userId, couponId)
                .map(userCouponMapper::toDomain);
    }

    @Override
    public List<UserCoupon> findAll() {
        return userCouponRepository.findAll().stream()
                .map(userCouponMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteAll() {
        userCouponRepository.deleteAll();
    }
}
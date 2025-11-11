package com.hhplus.be.usercoupon.infrastructure;

import com.hhplus.be.usercoupon.domain.UserCoupon;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class InMemoryUserCouponRepository implements UserCouponRepository {

    private final Map<Long, UserCoupon> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (userCoupon.getId() == null) {
            assignId(userCoupon, idGenerator.getAndIncrement());
        }
        storage.put(userCoupon.getId(), userCoupon);
        return userCoupon;
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return storage.values().stream()
                .filter(uc -> uc.getUserId().equals(userId))
                .sorted(Comparator.comparing(UserCoupon::getIssuedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<UserCoupon> findByUserIdAndUsed(Long userId, boolean used) {
        return storage.values().stream()
                .filter(uc -> uc.getUserId().equals(userId))
                .filter(uc -> uc.isUsed() == used)
                .sorted(Comparator.comparing(UserCoupon::getIssuedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        return storage.values().stream()
                .filter(uc -> uc.getUserId().equals(userId))
                .filter(uc -> uc.getCouponId().equals(couponId))
                .findFirst();
    }

    @Override
    public List<UserCoupon> findAll() {
        return new ArrayList<>(storage.values());
    }

    private void assignId(UserCoupon userCoupon, Long id) {
        try {
            Field field = UserCoupon.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(userCoupon, id);
        } catch (Exception e) {
            throw new RuntimeException("ID 할당 실패", e);
        }
    }
}
package com.hhplus.be.coupon.infrastructure;

import com.hhplus.be.coupon.domain.Coupon;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryCouponRepository implements CouponRepository {

    private final Map<Long, Coupon> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null) {
            assignId(coupon, idGenerator.getAndIncrement());
        }
        storage.put(coupon.getId(), coupon);
        return coupon;
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<Coupon> findByCode(String code) {
        return storage.values().stream()
                .filter(coupon -> coupon.getCode().equals(code))
                .findFirst();
    }

    @Override
    public List<Coupon> findAll() {
        return new ArrayList<>(storage.values());
    }

    private void assignId(Coupon coupon, Long id) {
        try {
            Field field = Coupon.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(coupon, id);
        } catch (Exception e) {
            throw new RuntimeException("ID 할당 실패", e);
        }
    }
}
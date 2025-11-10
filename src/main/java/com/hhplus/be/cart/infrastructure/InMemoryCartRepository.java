package com.hhplus.be.cart.infrastructure;

import com.hhplus.be.cart.domain.CartItem;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryCartRepository implements CartRepository {
    private final Map<Long, CartItem> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public CartItem save(CartItem cartItem) {
        if (cartItem.getId() == null) {
            // 신규 저장
            Long id = idGenerator.getAndIncrement();
            CartItem newItem = CartItem.create(cartItem.getUserId(), cartItem.getProductId(), cartItem.getQuantity());
            assignId(newItem, id);
            store.put(id, newItem);
            return newItem;
        } else {
            // 업데이트
            store.put(cartItem.getId(), cartItem);
            return cartItem;
        }
    }

    @Override
    public List<CartItem> findByUserId(Long userId) {
        return store.values().stream()
                .filter(item -> item.getUserId().equals(userId))
                .toList();
    }

    @Override
    public Optional<CartItem> findById(Long cartItemId) {
        return Optional.ofNullable(store.get(cartItemId));
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
        return store.values().stream()
                .filter(item -> item.getUserId().equals(userId) && item.getProductId().equals(productId))
                .findFirst();
    }

    @Override
    public void delete(CartItem cartItem) {
        store.remove(cartItem.getId());
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        store.values().removeIf(item -> item.getUserId().equals(userId));
    }

    /**
     * ID 할당을 위한 리플렉션 헬퍼 메서드
     */
    private void assignId(CartItem cartItem, Long id) {
        try {
            var field = CartItem.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(cartItem, id);
        } catch (Exception e) {
            throw new RuntimeException("ID 할당 실패", e);
        }
    }
}

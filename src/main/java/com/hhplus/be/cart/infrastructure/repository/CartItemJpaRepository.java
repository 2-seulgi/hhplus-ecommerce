package com.hhplus.be.cart.infrastructure.repository;

import com.hhplus.be.cart.infrastructure.entity.CartItemJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemJpaRepository extends JpaRepository<CartItemJpaEntity, Long> {
    List<CartItemJpaEntity> findByUserId(Long userId);
    Optional<CartItemJpaEntity> findByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserId(Long userId);
}

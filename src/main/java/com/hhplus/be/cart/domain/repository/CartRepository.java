package com.hhplus.be.cart.domain.repository;

import com.hhplus.be.cart.domain.model.CartItem;

import java.util.List;
import java.util.Optional;

public interface CartRepository {

    /**
     * 장바구니 아이템 저장 (신규 또는 업데이트)
     */
    CartItem save(CartItem cartItem);

    /**
     * 사용자 ID로 장바구니 아이템 모두 찾기
     */
    List<CartItem> findByUserId(Long userId);

    /**
     * 장바구니 아이템 ID로 조회
     */
    Optional<CartItem> findById(Long cartItemId);

    /**
     * 사용자 ID와 상품 ID로 장바구니 아이템 조회
     */
    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    /**
     * 장바구니 아이템 삭제
     */
    void delete(CartItem cartItem);

    /**
     * 사용자의 모든 장바구니 아이템 삭제
     */
    void deleteAllByUserId(Long userId);
}


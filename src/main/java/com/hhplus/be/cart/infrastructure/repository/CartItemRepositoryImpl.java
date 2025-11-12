package com.hhplus.be.cart.infrastructure.repository;

import com.hhplus.be.cart.domain.model.CartItem;
import com.hhplus.be.cart.domain.repository.CartRepository;
import com.hhplus.be.cart.infrastructure.mapper.CartItemMapper;
import com.hhplus.be.user.infrastructure.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CartItemRepositoryImpl implements CartRepository {
    private final CartItemJpaRepository cartItemJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final CartItemMapper cartItemMapper;

    @Override
    public CartItem save(CartItem cartItem){
        var entity = cartItemMapper.toEntity(cartItem);
        var savedEntity = cartItemJpaRepository.save(entity);
        return cartItemMapper.toDomain(savedEntity);
    }

    @Override
    public List<CartItem> findByUserId(Long userId) {
        return cartItemJpaRepository.findByUserId(userId).stream()
                .map(cartItemMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<CartItem> findById(Long cartItemId) {
        return cartItemJpaRepository.findById(cartItemId)
                .map(cartItemMapper::toDomain);
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
        return cartItemJpaRepository.findByUserIdAndProductId(userId, productId)
                .map(cartItemMapper::toDomain);
    }

    @Override
    public void delete(CartItem cartItem) {
        if (cartItem.getId() != null) {
            cartItemJpaRepository.deleteById(cartItem.getId());
        } else {
            cartItemJpaRepository.delete(cartItemMapper.toEntity(cartItem));
        }
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        cartItemJpaRepository.deleteByUserId(userId);
    }

}

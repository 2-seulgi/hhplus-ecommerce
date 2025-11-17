package com.hhplus.be.cart.infrastructure.repository;

import com.hhplus.be.cart.domain.model.CartItem;
import com.hhplus.be.cart.domain.repository.CartRepository;
import com.hhplus.be.cart.infrastructure.mapper.CartItemMapper;
import com.hhplus.be.user.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CartItemRepositoryImpl implements CartRepository {
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final CartItemMapper cartItemMapper;

    @Override
    public CartItem save(CartItem cartItem){
        var entity = cartItemMapper.toEntity(cartItem);
        var savedEntity = cartItemRepository.save(entity);
        return cartItemMapper.toDomain(savedEntity);
    }

    @Override
    public List<CartItem> findByUserId(Long userId) {
        return cartItemRepository.findByUserId(userId).stream()
                .map(cartItemMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<CartItem> findById(Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .map(cartItemMapper::toDomain);
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .map(cartItemMapper::toDomain);
    }

    @Override
    public void delete(CartItem cartItem) {
        if (cartItem.getId() != null) {
            cartItemRepository.deleteById(cartItem.getId());
        } else {
            cartItemRepository.delete(cartItemMapper.toEntity(cartItem));
        }
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        cartItemRepository.deleteByUserId(userId);
    }

    @Override
    public void deleteAll() {
        cartItemRepository.deleteAll();
    }

}

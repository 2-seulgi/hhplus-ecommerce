package com.hhplus.be.cart.infrastructure.mapper;

import com.hhplus.be.cart.domain.model.CartItem;
import com.hhplus.be.cart.infrastructure.entity.CartItemJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class CartItemMapper {

    public CartItem toDomain(CartItemJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return CartItem.reconstruct(
                entity.getId(),
                entity.getUserId(),
                entity.getProductId(),
                entity.getQuantity(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public CartItemJpaEntity toEntity(CartItem domain) {
        if (domain == null) {
            return null;
        }
        return new CartItemJpaEntity(
                domain.getId(),
                domain.getUserId(),
                domain.getProductId(),
                domain.getQuantity(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }

}

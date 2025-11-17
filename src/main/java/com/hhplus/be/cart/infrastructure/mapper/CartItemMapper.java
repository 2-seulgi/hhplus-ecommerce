package com.hhplus.be.cart.infrastructure.mapper;

import com.hhplus.be.cart.infrastructure.entity.CartItem;
import org.springframework.stereotype.Component;

@Component
public class CartItemMapper {

    public com.hhplus.be.cart.domain.model.CartItem toDomain(CartItem entity) {
        if (entity == null) {
            return null;
        }
        return com.hhplus.be.cart.domain.model.CartItem.reconstruct(
                entity.getId(),
                entity.getUserId(),
                entity.getProductId(),
                entity.getQuantity(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public CartItem toEntity(com.hhplus.be.cart.domain.model.CartItem domain) {
        if (domain == null) {
            return null;
        }
        return new CartItem(
                domain.getId(),
                domain.getUserId(),
                domain.getProductId(),
                domain.getQuantity(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }

}

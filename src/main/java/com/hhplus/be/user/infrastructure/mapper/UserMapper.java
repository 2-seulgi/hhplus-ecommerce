package com.hhplus.be.user.infrastructure.mapper;

import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.infrastructure.entity.ProductJpaEntity;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.user.infrastructure.entity.UserJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toDomain(UserJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return User.reconstruct(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getBalance(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * Domain Model → JPA Entity
     */
    public UserJpaEntity toEntity(User domain) {
        if (domain == null) {
            return null;
        }
        return new UserJpaEntity(
                domain.getId(),
                domain.getName(),
                domain.getEmail(),
                domain.getBalance(),
                domain.getVersion(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}

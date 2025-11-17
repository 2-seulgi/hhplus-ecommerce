package com.hhplus.be.user.infrastructure.mapper;

import com.hhplus.be.user.infrastructure.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public com.hhplus.be.user.domain.model.User toDomain(User entity) {
        if (entity == null) {
            return null;
        }
        return com.hhplus.be.user.domain.model.User.reconstruct(
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
    public User toEntity(com.hhplus.be.user.domain.model.User domain) {
        if (domain == null) {
            return null;
        }
        return new User(
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

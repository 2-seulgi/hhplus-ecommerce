package com.hhplus.be.point.infrastructure.mapper;

import com.hhplus.be.point.domain.model.Point;
import com.hhplus.be.point.infrastructure.entity.PointJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PointMapper {

    public Point toDomain(PointJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Point.reconstruct(
                entity.getId(),
                entity.getUserId(),
                entity.getPointType(),
                entity.getAmount(),
                entity.getBalanceAfter(),
                entity.getCreatedAt()
        );
    }

    public PointJpaEntity toEntity(Point domain) {
        if (domain == null) {
            return null;
        }
        return new PointJpaEntity(
                domain.getUserId(),
                domain.getPointType(),
                domain.getAmount(),
                domain.getBalanceAfter(),
                domain.getCreatedAt()
        );
    }
}

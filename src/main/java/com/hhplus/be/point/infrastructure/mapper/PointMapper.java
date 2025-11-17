package com.hhplus.be.point.infrastructure.mapper;

import com.hhplus.be.point.infrastructure.entity.Point;
import org.springframework.stereotype.Component;

@Component
public class PointMapper {

    public com.hhplus.be.point.domain.model.Point toDomain(Point entity) {
        if (entity == null) {
            return null;
        }
        return com.hhplus.be.point.domain.model.Point.reconstruct(
                entity.getId(),
                entity.getUserId(),
                entity.getPointType(),
                entity.getAmount(),
                entity.getBalanceAfter(),
                entity.getCreatedAt()
        );
    }

    public Point toEntity(com.hhplus.be.point.domain.model.Point domain) {
        if (domain == null) {
            return null;
        }
        return new Point(
                domain.getUserId(),
                domain.getPointType(),
                domain.getAmount(),
                domain.getBalanceAfter(),
                domain.getCreatedAt()
        );
    }
}

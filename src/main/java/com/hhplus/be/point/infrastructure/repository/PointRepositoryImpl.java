package com.hhplus.be.point.infrastructure.repository;

import com.hhplus.be.point.domain.model.Point;
import com.hhplus.be.point.domain.repository.PointRepository;
import com.hhplus.be.point.infrastructure.mapper.PointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Point Repository 구현체
 * Domain 인터페이스와 JPA 사이의 어댑터 역할
 */
@Repository
@RequiredArgsConstructor
public class PointRepositoryImpl implements PointRepository {

    private final PointJpaRepository pointJpaRepository;
    private final PointMapper pointMapper;

    @Override
    public Point save(Point point) {
        var entity = pointMapper.toEntity(point);
        var savedEntity = pointJpaRepository.save(entity);
        return pointMapper.toDomain(savedEntity);
    }

    @Override
    public List<Point> findByUserIdOrderByCreatedAtDesc(Long userId) {
        return pointJpaRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(pointMapper::toDomain)
                .collect(Collectors.toList());
    }
}
package com.hhplus.be.point.infrastructure;

import com.hhplus.be.point.domain.Point;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryPointRepository implements PointRepository {
    private final Map<Long, Point> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Point save(Point point) {
        Long id = idGenerator.getAndIncrement();
        point.assignId(id);  // ID 자동 생성 (package-private 메서드)
        store.put(id, point);
        return point;
    }

    @Override
    public List<Point> findByUserIdOrderByCreatedAtDesc(Long userId) {
        return store.values().stream()
                .filter(point -> point.getUserId().equals(userId))
                .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                .toList();
    }
}

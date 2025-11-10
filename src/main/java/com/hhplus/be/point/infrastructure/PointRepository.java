package com.hhplus.be.point.infrastructure;

import com.hhplus.be.point.domain.Point;

import java.util.List;

public interface PointRepository {
    // 1. 저장: 포인트 내역 저장
    Point save(Point point);
    // 2. 조회: 사용자 ID로 포인트 내역 모두 찾기 (최신순)
    List<Point> findByUserIdOrderByCreatedAtDesc(Long userId);

}

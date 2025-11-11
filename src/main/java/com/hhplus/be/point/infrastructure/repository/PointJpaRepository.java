package com.hhplus.be.point.infrastructure.repository;

import com.hhplus.be.point.infrastructure.entity.PointJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointJpaRepository extends JpaRepository<PointJpaEntity, Long> {
    // 사용자 ID로 포인트 내역 조회 (최신순)
    List<PointJpaEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}

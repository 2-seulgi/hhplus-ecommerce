package com.hhplus.be.point.service;

import com.hhplus.be.point.service.dto.*;
import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.point.domain.Point;
import com.hhplus.be.point.infrastructure.PointRepository;
import com.hhplus.be.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 포인트 관련 UseCase를 처리하는 Service
 * - 포인트 충전
 * - 포인트 내역 조회
 * - 포인트 잔액 조회
 */
@Service
@RequiredArgsConstructor
public class PointService {
    private final UserRepository userRepository;
    private final PointRepository pointRepository;

    /**
     * 포인트 충전 UseCase
     * API: POST /api/points/{userId}/charge
     */
    public PointChargeResult charge(PointChargeCommand command) {
        // 1. 사용자 조회
        var user = userRepository.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다"));

        // 2. 사용자 포인트 충전
        user.charge(command.amount());

        // 3. 포인트 히스토리 기록
        var point = Point.charge(command.userId(), command.amount(), user.getBalance());
        pointRepository.save(point);

        // 4. 변경된 사용자 정보 저장
        userRepository.save(user);

        // 5. 결과 반환
        return new PointChargeResult(point);
    }

    /**
     * 포인트 내역 조회 UseCase
     * API: GET /api/points/{userId}/history
     */
    public List<PointHistoryResult> getHistory(PointHistoryQuery query) {
        // 1. 포인트 내역 조회 (최신순)
        var points = pointRepository.findByUserIdOrderByCreatedAtDesc(query.userId());

        // 2. DTO로 변환
        return points.stream()
                .map(PointHistoryResult::new)
                .toList();
    }

    /**
     * 포인트 잔액 조회 UseCase
     * API: GET /api/points/{userId}
     */
    public PointBalanceResult getBalance(PointBalanceQuery query) {
        // 1. 사용자 조회
        var user = userRepository.findById(query.userId())
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다"));

        // 2. 잔액 반환
        return new PointBalanceResult(user.getId(), user.getBalance());
    }
}
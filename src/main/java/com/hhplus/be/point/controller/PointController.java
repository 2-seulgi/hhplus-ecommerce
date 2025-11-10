package com.hhplus.be.point.controller;

import com.hhplus.be.point.service.PointService;
import com.hhplus.be.point.service.dto.*;
import com.hhplus.be.point.controller.dto.PointBalanceResponse;
import com.hhplus.be.point.controller.dto.PointChargeRequest;
import com.hhplus.be.point.controller.dto.PointChargeResponse;
import com.hhplus.be.point.controller.dto.PointHistoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 포인트 API Controller
 * - 포인트 충전
 * - 포인트 내역 조회
 * - 포인트 잔액 조회
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/points")
@RequiredArgsConstructor
public class PointController {
    private final PointService pointService;

    /**
     * 1.1. 포인트 잔액 조회
     * GET /api/v1/users/{userId}/points/balance
     */
    @GetMapping("/balance")
    public ResponseEntity<PointBalanceResponse> getBalance(@PathVariable Long userId) {
        var query = new PointBalanceQuery(userId);
        var result = pointService.getBalance(query);
        return ResponseEntity.ok(PointBalanceResponse.from(result));
    }

    /**
     * 1.2. 포인트 충전
     * POST /api/v1/users/{userId}/points/charge
     */
    @PostMapping("/charge")
    public ResponseEntity<PointChargeResponse> charge(
            @PathVariable Long userId,
            @RequestBody @Valid PointChargeRequest request
    ) {
        var command = new PointChargeCommand(userId, request.amount());
        var result = pointService.charge(command);
        return ResponseEntity.ok(PointChargeResponse.from(result));
    }

    /**
     * 1.3. 포인트 내역 조회
     * GET /api/v1/users/{userId}/points/history?page=0&size=20
     */
    @GetMapping("/history")
    public ResponseEntity<Page<PointHistoryResponse>> getHistory(
            @PathVariable Long userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        var query = new PointHistoryQuery(userId);
        List<PointHistoryResult> history = pointService.getHistory(query);

        // 페이징 처리 (간단 버전: 전체 조회 후 페이징)
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), history.size());

        List<PointHistoryResponse> pageContent = history.subList(start, end).stream()
                .map(PointHistoryResponse::from)
                .toList();
        Page<PointHistoryResponse> page = new PageImpl<>(pageContent, pageable, history.size());

        return ResponseEntity.ok(page);
    }
}
package com.hhplus.be.usercoupon.controller;

import com.hhplus.be.coupon.service.CouponQueueService;
import com.hhplus.be.usercoupon.controller.dto.GetUserCouponsResponse;
import com.hhplus.be.usercoupon.controller.dto.IssueCouponQueueResponse;
import com.hhplus.be.usercoupon.service.UserCouponService;
import com.hhplus.be.usercoupon.service.dto.GetUserCouponsQuery;
import com.hhplus.be.usercoupon.service.dto.GetUserCouponsResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 쿠폰 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users/{userId}/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final UserCouponService userCouponService;
    private final CouponQueueService couponQueueService;

    /**
     * 선착순 쿠폰 발급 (비동기 큐 방식 - 단순화)
     *
     * POST /users/{userId}/coupons/{couponId}/issue
     *
     * 개선된 프로세스:
     * 1. Redis Stream에 요청 메시지만 추가 (XADD)
     * 2. 202 Accepted 즉시 응답
     * 3. Worker가 비동기로:
     *    - 선착순 검증 (DB 기반)
     *    - 중복 발급 검증 (DB 기반)
     *    - 실제 쿠폰 발급
     * 4. 결과는 GET /result로 폴링
     *
     * 응답:
     * - 202 Accepted: 발급 요청 접수됨 (항상)
     */
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<IssueCouponQueueResponse> issueCoupon(
            @PathVariable Long userId,
            @PathVariable Long couponId
    ) {
        log.info("쿠폰 발급 요청 - userId: {}, couponId: {}", userId, couponId);

        // Stream에 발급 요청 추가
        couponQueueService.enqueue(couponId, userId);

        // 발급 처리 중 (202 Accepted)
        log.info("발급 요청 접수 - userId: {}, couponId: {}", userId, couponId);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(IssueCouponQueueResponse.accepted(
                        null, // position은 Worker가 결정
                        "발급 요청이 접수되었습니다",
                        "Check result: GET /api/v1/users/" + userId + "/coupons/" + couponId + "/result"
                ));
    }

    /**
     * 쿠폰 발급 결과 조회
     *
     * GET /users/{userId}/coupons/{couponId}/result
     *
     * 응답:
     * - SUCCESS: 발급 성공
     * - FAILED: 발급 실패
     * - null: 처리 중 (폴링 계속)
     */
    @GetMapping("/{couponId}/result")
    public ResponseEntity<String> getCouponIssueResult(
            @PathVariable Long userId,
            @PathVariable Long couponId
    ) {
        String result = couponQueueService.getResult(userId, couponId);

        if (result == null) {
            // 처리 중
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body("PROCESSING");
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 보유 쿠폰 조회
     *
     * GET /users/{userId}/coupons?available=false
     */
    @GetMapping
    public ResponseEntity<GetUserCouponsResponse> getUserCoupons(
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "false") Boolean available
    ) {
        GetUserCouponsQuery query = new GetUserCouponsQuery(userId, available);
        GetUserCouponsResult result = userCouponService.getUserCoupons(query);
        return ResponseEntity.ok(GetUserCouponsResponse.from(result));
    }
}
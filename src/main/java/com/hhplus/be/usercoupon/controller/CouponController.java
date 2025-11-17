package com.hhplus.be.usercoupon.controller;

import com.hhplus.be.usercoupon.controller.dto.GetUserCouponsResponse;
import com.hhplus.be.usercoupon.controller.dto.IssueCouponResponse;
import com.hhplus.be.usercoupon.service.UserCouponService;
import com.hhplus.be.usercoupon.service.dto.GetUserCouponsQuery;
import com.hhplus.be.usercoupon.service.dto.GetUserCouponsResult;
import com.hhplus.be.usercoupon.service.dto.IssueCouponCommand;
import com.hhplus.be.usercoupon.service.dto.IssueCouponResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 쿠폰 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final UserCouponService userCouponService;

    /**
     * 선착순 쿠폰 발급
     *
     * POST /users/{userId}/coupons/{couponId}/issue
     */
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<IssueCouponResponse> issueCoupon(
            @PathVariable Long userId,
            @PathVariable Long couponId
    ) {
        IssueCouponCommand command = new IssueCouponCommand(userId, couponId);
        IssueCouponResult result = userCouponService.issueCoupon(command);
        return ResponseEntity.ok(IssueCouponResponse.from(result));
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
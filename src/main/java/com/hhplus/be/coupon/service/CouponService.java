package com.hhplus.be.coupon.service;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.coupon.domain.Coupon;
import com.hhplus.be.coupon.domain.DiscountType;
import com.hhplus.be.coupon.infrastructure.CouponRepository;
import com.hhplus.be.coupon.service.dto.DiscountCalculationResult;
import com.hhplus.be.coupon.service.dto.ValidateDiscountCommand;
import com.hhplus.be.usercoupon.UserCoupon;
import com.hhplus.be.usercoupon.infrastructure.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CouponService {
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final Clock clock; // 테스트용 주입


    public DiscountCalculationResult validateAndCalculateDiscount(ValidateDiscountCommand command) {
        if (command.couponCode() == null || command.couponCode().isBlank()) {
            return DiscountCalculationResult.noDiscount();
        }
        // 1. 쿠폰 존재 여부 확인
        var coupon = couponRepository.findByCode(command.couponCode())
                .orElseThrow(() -> new BusinessException("유효하지 않은 쿠폰입니다.", "COUPON_INVALID"));
        // 2. 사용 기간 검증
        Instant now = Instant.now(clock);
        if(now.isBefore(coupon.getUseStartAt())|| now.isAfter(coupon.getUseEndAt())) {
            throw new BusinessException("쿠폰 사용 기간이 아닙니다.", "COUPON_NOT_IN_USE_PERIOD");
        }

        // 3. 사용자 쿠폰 조회 및 검증
        UserCoupon userCoupon = userCouponRepository
                .findByUserIdAndCouponId(command.userId(), coupon.getId())
                .orElseThrow(() -> new BusinessException("보유하지 않은 쿠폰", "COUPON_INVALID"));

        if (userCoupon.isUsed()) {
            throw new BusinessException("이미 사용된 쿠폰", "COUPON_ALREADY_USED");
        }

        // 4. 할인 금액 계산
        int discountAmount = calculateDiscount(coupon, command.orderAmount());

        return new DiscountCalculationResult(
                userCoupon.getId(),
                coupon.getId(),
                coupon.getDiscountValue(),
                discountAmount
        );
    }

    /**
     * 쿠폰 사용 처리
     */
    public void markAsUsed(Long userCouponId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new ResourceNotFoundException("쿠폰을 찾을 수 없습니다"));
        userCoupon.use();
    }

    private int calculateDiscount(Coupon coupon, int orderAmount) {
        if (coupon.getDiscountType() == DiscountType.FIXED) {
            return coupon.getDiscountValue();
        } else if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            return orderAmount * coupon.getDiscountValue() / 100;
        }
        return 0;
    }



}

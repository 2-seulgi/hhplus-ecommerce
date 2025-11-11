package com.hhplus.be.coupon.service;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.coupon.domain.Coupon;
import com.hhplus.be.coupon.domain.DiscountType;
import com.hhplus.be.coupon.infrastructure.CouponRepository;
import com.hhplus.be.coupon.service.dto.DiscountCalculationResult;
import com.hhplus.be.coupon.service.dto.ValidateDiscountCommand;
import com.hhplus.be.usercoupon.domain.UserCoupon;
import com.hhplus.be.usercoupon.infrastructure.UserCouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CouponService 단위 테스트
 * 할인 계산 및 쿠폰 검증 로직 테스트
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private CouponService couponService;

    @Test
    @DisplayName("할인 계산 성공 - FIXED 타입")
    void validateAndCalculateDiscount_fixedType_success() {
        // Given
        Long userId = 1L;
        String couponCode = "FIXED1000";
        int orderAmount = 50000;
        Instant now = Instant.parse("2025-11-11T00:00:00Z");

        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());

        Coupon coupon = Coupon.create(
                couponCode,
                "1000원 할인",
                DiscountType.FIXED,
                1000,  // 고정 1000원 할인
                100,
                10,
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                now.minusSeconds(3600),
                now.plusSeconds(86400)
        );

        UserCoupon userCoupon = UserCoupon.create(userId, coupon.getId(), now.minusSeconds(1800));

        when(couponRepository.findByCode(couponCode)).thenReturn(Optional.of(coupon));
        when(userCouponRepository.findByUserIdAndCouponId(userId, coupon.getId()))
                .thenReturn(Optional.of(userCoupon));

        // When
        ValidateDiscountCommand command = new ValidateDiscountCommand(userId, couponCode, orderAmount);
        DiscountCalculationResult result = couponService.validateAndCalculateDiscount(command);

        // Then
        assertThat(result.userCouponId()).isEqualTo(userCoupon.getId());
        assertThat(result.couponId()).isEqualTo(coupon.getId());
        assertThat(result.discountValue()).isEqualTo(1000);
        assertThat(result.discountAmount()).isEqualTo(1000);  // FIXED는 고정 금액
    }

    @Test
    @DisplayName("할인 계산 성공 - PERCENTAGE 타입")
    void validateAndCalculateDiscount_percentageType_success() {
        // Given
        Long userId = 1L;
        String couponCode = "PERCENT10";
        int orderAmount = 50000;
        Instant now = Instant.parse("2025-11-11T00:00:00Z");

        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());

        Coupon coupon = Coupon.create(
                couponCode,
                "10% 할인",
                DiscountType.PERCENTAGE,
                10,  // 10% 할인
                100,
                10,
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                now.minusSeconds(3600),
                now.plusSeconds(86400)
        );

        UserCoupon userCoupon = UserCoupon.create(userId, coupon.getId(), now.minusSeconds(1800));

        when(couponRepository.findByCode(couponCode)).thenReturn(Optional.of(coupon));
        when(userCouponRepository.findByUserIdAndCouponId(userId, coupon.getId()))
                .thenReturn(Optional.of(userCoupon));

        // When
        ValidateDiscountCommand command = new ValidateDiscountCommand(userId, couponCode, orderAmount);
        DiscountCalculationResult result = couponService.validateAndCalculateDiscount(command);

        // Then
        assertThat(result.userCouponId()).isEqualTo(userCoupon.getId());
        assertThat(result.couponId()).isEqualTo(coupon.getId());
        assertThat(result.discountValue()).isEqualTo(10);
        assertThat(result.discountAmount()).isEqualTo(5000);  // 50000 * 10% = 5000
    }

    @Test
    @DisplayName("할인 계산 - 쿠폰 코드 없음 (할인 없음)")
    void validateAndCalculateDiscount_noCouponCode_noDiscount() {
        // Given
        Long userId = 1L;
        String couponCode = null;
        int orderAmount = 50000;

        // When
        ValidateDiscountCommand command = new ValidateDiscountCommand(userId, couponCode, orderAmount);
        DiscountCalculationResult result = couponService.validateAndCalculateDiscount(command);

        // Then
        assertThat(result.userCouponId()).isNull();
        assertThat(result.couponId()).isNull();
        assertThat(result.discountValue()).isZero();
        assertThat(result.discountAmount()).isZero();
    }

    @Test
    @DisplayName("할인 계산 - 쿠폰 코드 빈 문자열 (할인 없음)")
    void validateAndCalculateDiscount_emptyCouponCode_noDiscount() {
        // Given
        Long userId = 1L;
        String couponCode = "   ";
        int orderAmount = 50000;

        // When
        ValidateDiscountCommand command = new ValidateDiscountCommand(userId, couponCode, orderAmount);
        DiscountCalculationResult result = couponService.validateAndCalculateDiscount(command);

        // Then
        assertThat(result.userCouponId()).isNull();
        assertThat(result.couponId()).isNull();
        assertThat(result.discountValue()).isZero();
        assertThat(result.discountAmount()).isZero();
    }

    @Test
    @DisplayName("할인 계산 실패 - 유효하지 않은 쿠폰")
    void validateAndCalculateDiscount_invalidCoupon_throwsException() {
        // Given
        Long userId = 1L;
        String couponCode = "INVALID";
        int orderAmount = 50000;

        when(couponRepository.findByCode(couponCode)).thenReturn(Optional.empty());

        // When & Then
        ValidateDiscountCommand command = new ValidateDiscountCommand(userId, couponCode, orderAmount);
        assertThatThrownBy(() -> couponService.validateAndCalculateDiscount(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않은 쿠폰");
    }

    @Test
    @DisplayName("할인 계산 실패 - 사용 기간이 아님 (시작 전)")
    void validateAndCalculateDiscount_beforeUsePeriod_throwsException() {
        // Given
        Long userId = 1L;
        String couponCode = "FUTURE";
        int orderAmount = 50000;
        Instant now = Instant.parse("2025-11-11T00:00:00Z");

        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());

        Coupon coupon = Coupon.create(
                couponCode,
                "미래 쿠폰",
                DiscountType.FIXED,
                1000,
                100,
                10,
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                now.plusSeconds(3600),  // 사용 시작이 1시간 후
                now.plusSeconds(90000)
        );

        when(couponRepository.findByCode(couponCode)).thenReturn(Optional.of(coupon));

        // When & Then
        ValidateDiscountCommand command = new ValidateDiscountCommand(userId, couponCode, orderAmount);
        assertThatThrownBy(() -> couponService.validateAndCalculateDiscount(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용 기간이 아닙니다");
    }

    @Test
    @DisplayName("할인 계산 실패 - 사용 기간이 아님 (종료 후)")
    void validateAndCalculateDiscount_afterUsePeriod_throwsException() {
        // Given
        Long userId = 1L;
        String couponCode = "EXPIRED";
        int orderAmount = 50000;
        Instant now = Instant.parse("2025-11-11T00:00:00Z");

        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());

        Coupon coupon = Coupon.create(
                couponCode,
                "만료된 쿠폰",
                DiscountType.FIXED,
                1000,
                100,
                10,
                now.minusSeconds(10000),
                now.minusSeconds(5000),
                now.minusSeconds(10000),
                now.minusSeconds(3600)  // 사용 종료가 1시간 전
        );

        when(couponRepository.findByCode(couponCode)).thenReturn(Optional.of(coupon));

        // When & Then
        ValidateDiscountCommand command = new ValidateDiscountCommand(userId, couponCode, orderAmount);
        assertThatThrownBy(() -> couponService.validateAndCalculateDiscount(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용 기간이 아닙니다");
    }

    @Test
    @DisplayName("할인 계산 실패 - 보유하지 않은 쿠폰")
    void validateAndCalculateDiscount_notOwnedCoupon_throwsException() {
        // Given
        Long userId = 1L;
        String couponCode = "NOTOWNED";
        int orderAmount = 50000;
        Instant now = Instant.parse("2025-11-11T00:00:00Z");

        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());

        Coupon coupon = Coupon.create(
                couponCode,
                "미보유 쿠폰",
                DiscountType.FIXED,
                1000,
                100,
                10,
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                now.minusSeconds(3600),
                now.plusSeconds(86400)
        );

        when(couponRepository.findByCode(couponCode)).thenReturn(Optional.of(coupon));
        when(userCouponRepository.findByUserIdAndCouponId(userId, coupon.getId()))
                .thenReturn(Optional.empty());

        // When & Then
        ValidateDiscountCommand command = new ValidateDiscountCommand(userId, couponCode, orderAmount);
        assertThatThrownBy(() -> couponService.validateAndCalculateDiscount(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("보유하지 않은 쿠폰");
    }

    @Test
    @DisplayName("할인 계산 실패 - 이미 사용된 쿠폰")
    void validateAndCalculateDiscount_alreadyUsedCoupon_throwsException() {
        // Given
        Long userId = 1L;
        String couponCode = "USED";
        int orderAmount = 50000;
        Instant now = Instant.parse("2025-11-11T00:00:00Z");

        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());

        Coupon coupon = Coupon.create(
                couponCode,
                "사용된 쿠폰",
                DiscountType.FIXED,
                1000,
                100,
                10,
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                now.minusSeconds(3600),
                now.plusSeconds(86400)
        );

        UserCoupon userCoupon = UserCoupon.create(userId, coupon.getId(), now.minusSeconds(1800));
        userCoupon.use();  // 쿠폰 사용 처리

        when(couponRepository.findByCode(couponCode)).thenReturn(Optional.of(coupon));
        when(userCouponRepository.findByUserIdAndCouponId(userId, coupon.getId()))
                .thenReturn(Optional.of(userCoupon));

        // When & Then
        ValidateDiscountCommand command = new ValidateDiscountCommand(userId, couponCode, orderAmount);
        assertThatThrownBy(() -> couponService.validateAndCalculateDiscount(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 사용된 쿠폰");
    }

    @Test
    @DisplayName("쿠폰 사용 처리 성공")
    void markAsUsed_success() {
        // Given
        Long userCouponId = 1L;
        Long userId = 1L;
        Long couponId = 10L;
        Instant now = Instant.now();

        UserCoupon userCoupon = UserCoupon.create(userId, couponId, now.minusSeconds(1800));
        when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.of(userCoupon));

        // When
        couponService.markAsUsed(userCouponId);

        // Then
        assertThat(userCoupon.isUsed()).isTrue();
        assertThat(userCoupon.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("쿠폰 사용 처리 실패 - 쿠폰을 찾을 수 없음")
    void markAsUsed_notFound_throwsException() {
        // Given
        Long userCouponId = 999L;
        when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> couponService.markAsUsed(userCouponId))
                .isInstanceOf(com.hhplus.be.common.exception.ResourceNotFoundException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
    }
}
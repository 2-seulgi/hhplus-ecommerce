package com.hhplus.be.usercoupon.service;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.coupon.domain.Coupon;
import com.hhplus.be.coupon.domain.DiscountType;
import com.hhplus.be.coupon.infrastructure.CouponRepository;
import com.hhplus.be.user.domain.User;
import com.hhplus.be.user.infrastructure.UserRepository;
import com.hhplus.be.usercoupon.UserCoupon;
import com.hhplus.be.usercoupon.infrastructure.UserCouponRepository;
import com.hhplus.be.usercoupon.service.dto.GetUserCouponsQuery;
import com.hhplus.be.usercoupon.service.dto.GetUserCouponsResult;
import com.hhplus.be.usercoupon.service.dto.IssueCouponCommand;
import com.hhplus.be.usercoupon.service.dto.IssueCouponResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private UserCouponRepository userCouponRepository;

    @InjectMocks
    private CouponService couponService;

    @Test
    @DisplayName("쿠폰 발급 성공")
    void issueCoupon_success() {
        // Given
        Long userId = 1L;
        Long couponId = 10L;
        Instant now = Instant.now();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.createWithId(userId, "홍길동", "hong@test.com", 10000)));

        Coupon coupon = Coupon.create(
                "WELCOME10", "신규회원 쿠폰", DiscountType.FIXED, 5000, 100, 0,
                now.minusSeconds(3600), now.plusSeconds(86400),
                now.minusSeconds(3600), now.plusSeconds(86400)
        );
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));
        when(userCouponRepository.findByUserIdAndCouponId(userId, couponId))
                .thenReturn(Optional.empty());

        when(userCouponRepository.save(any(UserCoupon.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        IssueCouponCommand command = new IssueCouponCommand(userId, couponId);
        IssueCouponResult result = couponService.issueCoupon(command);

        // Then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.couponId()).isEqualTo(couponId);
        assertThat(result.couponName()).isEqualTo("신규회원 쿠폰");
        assertThat(coupon.getIssuedQuantity()).isEqualTo(1);  // 발급 수 증가 확인
        verify(couponRepository).save(coupon);
        verify(userCouponRepository).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("동시에 10000요청해도 발급 수량이 총량을 초과하지 않는다")
    void concurrentIssue_noOversell() throws Exception {
        // Given
        Long couponId = 10L;
        int total = 10;
        int requests = 10000;

        // 쿠폰 초기화: 총 10개
        couponRepository.save(Coupon.create("CODE10","쿠폰",DiscountType.FIXED,1000,
                total, 0, Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600),
                Instant.now(), Instant.now().plusSeconds(7200)));

        ExecutorService es = Executors.newFixedThreadPool(64); // 64개 스레드풀
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requests);

        for (int i = 0; i < requests; i++) {
            long userId = i+1;
            es.submit(() -> {
                ready.countDown();
                try{
                    start.await();
                    couponService.issueCoupon(new IssueCouponCommand(userId, couponId));
                }catch(Exception e){

                }finally{
                    done.countDown();
                }
                return null;
            });
        }
        ready.await();
        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS));  // ✅ 15초로 수정
        es.shutdown();

        Coupon after = couponRepository.findById(couponId).orElseThrow();
        assertEquals(total, after.getIssuedQuantity(), "발급 수량은 총 재고를 초과하면 안 됨");

    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 사용자 없음")
    void issueCoupon_userNotFound() {
        // Given
        Long userId = 999L;
        Long couponId = 10L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        IssueCouponCommand command = new IssueCouponCommand(userId, couponId);
        assertThatThrownBy(() -> couponService.issueCoupon(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("회원");
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 쿠폰 없음")
    void issueCoupon_couponNotFound() {
        // Given
        Long userId = 1L;
        Long couponId = 999L;

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.createWithId(userId, "홍길동", "hong@test.com", 10000)));
        when(couponRepository.findById(couponId)).thenReturn(Optional.empty());

        // When & Then
        IssueCouponCommand command = new IssueCouponCommand(userId, couponId);
        assertThatThrownBy(() -> couponService.issueCoupon(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("쿠폰");
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 발급 기간 아님")
    void issueCoupon_notInIssuePeriod() {
        // Given
        Long userId = 1L;
        Long couponId = 10L;
        Instant now = Instant.now();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.createWithId(userId, "홍길동", "hong@test.com", 10000)));

        // 발급 기간이 지난 쿠폰
        Coupon coupon = Coupon.create(
                "EXPIRED", "만료된 쿠폰", DiscountType.FIXED, 5000, 100, 0,
                now.minusSeconds(7200), now.minusSeconds(3600),  // 이미 발급 기간 지남
                now.minusSeconds(3600), now.plusSeconds(86400)
        );
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));

        // When & Then
        IssueCouponCommand command = new IssueCouponCommand(userId, couponId);
        assertThatThrownBy(() -> couponService.issueCoupon(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("발급 기간");
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 이미 발급받음")
    void issueCoupon_alreadyIssued() {
        // Given
        Long userId = 1L;
        Long couponId = 10L;
        Instant now = Instant.now();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.createWithId(userId, "홍길동", "hong@test.com", 10000)));

        Coupon coupon = Coupon.create(
                "WELCOME10", "신규회원 쿠폰", DiscountType.FIXED, 5000, 100, 10,
                now.minusSeconds(3600), now.plusSeconds(86400),
                now.minusSeconds(3600), now.plusSeconds(86400)
        );
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));

        // 이미 발급받은 쿠폰
        UserCoupon existingUserCoupon = UserCoupon.create(userId, couponId, now.minusSeconds(1800));
        when(userCouponRepository.findByUserIdAndCouponId(userId, couponId))
                .thenReturn(Optional.of(existingUserCoupon));

        // When & Then
        IssueCouponCommand command = new IssueCouponCommand(userId, couponId);
        assertThatThrownBy(() -> couponService.issueCoupon(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 발급");
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 발급 수량 소진")
    void issueCoupon_soldOut() {
        // Given
        Long userId = 1L;
        Long couponId = 10L;
        Instant now = Instant.now();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.createWithId(userId, "홍길동", "hong@test.com", 10000)));

        // 발급 수량이 다 찬 쿠폰 (totalCount=100, issuedCount=100)
        Coupon coupon = Coupon.create(
                "SOLDOUT", "매진 쿠폰", DiscountType.FIXED, 5000, 100, 100,
                now.minusSeconds(3600), now.plusSeconds(86400),
                now.minusSeconds(3600), now.plusSeconds(86400)
        );
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));
        when(userCouponRepository.findByUserIdAndCouponId(userId, couponId))
                .thenReturn(Optional.empty());

        // When & Then
        IssueCouponCommand command = new IssueCouponCommand(userId, couponId);
        assertThatThrownBy(() -> couponService.issueCoupon(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("소진");
    }

    @Test
    @DisplayName("보유 쿠폰 조회 - 전체")
    void getUserCoupons_all() {
        // Given
        Long userId = 1L;
        Instant now = Instant.now();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.createWithId(userId, "홍길동", "hong@test.com", 10000)));

        UserCoupon uc1 = UserCoupon.create(userId, 10L, now.minusSeconds(1800));
        UserCoupon uc2 = UserCoupon.create(userId, 20L, now.minusSeconds(3600));
        uc2.use();  // 사용된 쿠폰

        when(userCouponRepository.findByUserId(userId)).thenReturn(List.of(uc1, uc2));

        Coupon coupon1 = Coupon.create(
                "COUPON1", "쿠폰1", DiscountType.FIXED, 5000, 100, 10,
                now.minusSeconds(7200), now.plusSeconds(86400),
                now.minusSeconds(7200), now.plusSeconds(86400)
        );
        Coupon coupon2 = Coupon.create(
                "COUPON2", "쿠폰2", DiscountType.PERCENTAGE, 10, 100, 20,
                now.minusSeconds(7200), now.plusSeconds(86400),
                now.minusSeconds(7200), now.plusSeconds(86400)
        );

        when(couponRepository.findById(10L)).thenReturn(Optional.of(coupon1));
        when(couponRepository.findById(20L)).thenReturn(Optional.of(coupon2));

        // When
        GetUserCouponsQuery query = new GetUserCouponsQuery(userId, false);
        GetUserCouponsResult result = couponService.getUserCoupons(query);

        // Then
        assertThat(result.coupons()).hasSize(2);
    }

    @Test
    @DisplayName("보유 쿠폰 조회 - 사용 가능한 쿠폰만")
    void getUserCoupons_availableOnly() {
        // Given
        Long userId = 1L;
        Instant now = Instant.now();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.createWithId(userId, "홍길동", "hong@test.com", 10000)));

        UserCoupon uc1 = UserCoupon.create(userId, 10L, now.minusSeconds(1800));
        UserCoupon uc2 = UserCoupon.create(userId, 20L, now.minusSeconds(3600));
        uc2.use();  // 사용된 쿠폰 (필터링되어야 함)

        when(userCouponRepository.findByUserId(userId)).thenReturn(List.of(uc1, uc2));

        Coupon coupon1 = Coupon.create(
                "COUPON1", "쿠폰1", DiscountType.FIXED, 5000, 100, 10,
                now.minusSeconds(7200), now.plusSeconds(86400),
                now.minusSeconds(7200), now.plusSeconds(86400)
        );
        Coupon coupon2 = Coupon.create(
                "COUPON2", "쿠폰2", DiscountType.PERCENTAGE, 10, 100, 20,
                now.minusSeconds(7200), now.plusSeconds(86400),
                now.minusSeconds(7200), now.plusSeconds(86400)
        );

        when(couponRepository.findById(10L)).thenReturn(Optional.of(coupon1));
        when(couponRepository.findById(20L)).thenReturn(Optional.of(coupon2));

        // When
        GetUserCouponsQuery query = new GetUserCouponsQuery(userId, true);
        GetUserCouponsResult result = couponService.getUserCoupons(query);

        // Then
        assertThat(result.coupons()).hasSize(1);  // 사용 안 된 쿠폰만
        assertThat(result.coupons().get(0).couponName()).isEqualTo("쿠폰1");
    }
}
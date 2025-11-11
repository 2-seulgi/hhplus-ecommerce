package com.hhplus.be.usercoupon.service;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.coupon.domain.Coupon;
import com.hhplus.be.coupon.domain.DiscountType;
import com.hhplus.be.coupon.infrastructure.CouponRepository;
import com.hhplus.be.user.domain.User;
import com.hhplus.be.user.infrastructure.UserRepository;
import com.hhplus.be.usercoupon.UserCoupon;
import com.hhplus.be.usercoupon.infrastructure.UserCouponRepository;
import com.hhplus.be.usercoupon.service.dto.IssueCouponCommand;
import com.hhplus.be.usercoupon.service.dto.IssueCouponResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * CouponService 동시성 통합 테스트
 *
 * 선착순 쿠폰 발급의 Race Condition 검증
 */
@SpringBootTest
class CouponServiceConcurrencyTest {

    @Autowired
    private UserCouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @BeforeEach
    void setUp() {
        // 테스트 격리를 위한 초기화는 인메모리 저장소 특성상 어려우므로
        // 각 테스트에서 새로운 쿠폰과 유저를 생성합니다
    }

    @Test
    @DisplayName("동시성 테스트: 100명이 선착순 10장 쿠폰을 동시에 발급받으면 정확히 10명만 성공")
    void concurrency_100Users_10Coupons_Only10Success() throws InterruptedException {
        // Given: 선착순 10장 쿠폰 생성
        Instant now = Instant.now();
        Coupon coupon = Coupon.create(
                "CONCURRENT_TEST_" + System.currentTimeMillis(),
                "선착순 10장 쿠폰",
                DiscountType.FIXED,
                5000,
                10,  // 총 10장
                0,   // 초기 발급 0장
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                now.minusSeconds(3600),
                now.plusSeconds(86400)
        );
        couponRepository.save(coupon);

        // 100명의 유저 생성
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            User user = User.createWithId(
                    (long) i,
                    "유저" + i,
                    "user" + i + "@test.com",
                    100000
            );
            userRepository.save(user);
            users.add(user);
        }

        // When: 100명이 동시에 쿠폰 발급 시도
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(100);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (User user : users) {
            executorService.submit(() -> {
                try {
                    IssueCouponCommand command = new IssueCouponCommand(user.getId(), coupon.getId());
                    couponService.issueCoupon(command);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getMessage().contains("소진")) {
                        failCount.incrementAndGet();
                    } else {
                        throw e;
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 정확히 10명만 성공, 90명은 실패
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(90);

        // 쿠폰 발급 수량 확인
        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(10);

        // UserCoupon 레코드 수 확인
        List<UserCoupon> issuedCoupons = userCouponRepository.findAll();
        long couponCount = issuedCoupons.stream()
                .filter(uc -> uc.getCouponId().equals(coupon.getId()))
                .count();
        assertThat(couponCount).isEqualTo(10);
    }

    @Test
    @DisplayName("동시성 테스트: 1명이 여러 쿠폰을 동시에 발급받아도 모두 성공")
    void concurrency_1User_MultipleCoupons_AllSuccess() throws InterruptedException {
        // Given: 5개의 쿠폰 생성
        Instant now = Instant.now();
        List<Coupon> coupons = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Coupon coupon = Coupon.create(
                    "MULTI_COUPON_" + System.currentTimeMillis() + "_" + i,
                    "쿠폰 " + i,
                    DiscountType.FIXED,
                    5000,
                    100,
                    0,
                    now.minusSeconds(3600),
                    now.plusSeconds(86400),
                    now.minusSeconds(3600),
                    now.plusSeconds(86400)
            );
            couponRepository.save(coupon);
            coupons.add(coupon);
        }

        // 1명의 유저 생성
        User user = User.createWithId(999L, "테스트유저", "test@test.com", 100000);
        userRepository.save(user);

        // When: 1명이 5개 쿠폰을 동시에 발급
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);

        AtomicInteger successCount = new AtomicInteger(0);

        for (Coupon coupon : coupons) {
            executorService.submit(() -> {
                try {
                    IssueCouponCommand command = new IssueCouponCommand(user.getId(), coupon.getId());
                    couponService.issueCoupon(command);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 5개 모두 성공
        assertThat(successCount.get()).isEqualTo(5);

        // 각 쿠폰의 발급 수량 확인
        for (Coupon coupon : coupons) {
            Coupon updated = couponRepository.findById(coupon.getId()).orElseThrow();
            assertThat(updated.getIssuedQuantity()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("동시성 테스트: 여러 유저가 여러 쿠폰을 동시에 발급받으면 모두 정상 처리")
    void concurrency_MultipleUsers_MultipleCoupons_AllSuccess() throws InterruptedException {
        // Given: 3개의 쿠폰 생성 (각 100장)
        Instant now = Instant.now();
        List<Coupon> coupons = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Coupon coupon = Coupon.create(
                    "MULTI_TEST_" + System.currentTimeMillis() + "_" + i,
                    "쿠폰 " + i,
                    DiscountType.FIXED,
                    5000,
                    100,
                    0,
                    now.minusSeconds(3600),
                    now.plusSeconds(86400),
                    now.minusSeconds(3600),
                    now.plusSeconds(86400)
            );
            couponRepository.save(coupon);
            coupons.add(coupon);
        }

        // 50명의 유저 생성
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            User user = User.createWithId(
                    (long) (1000 + i),
                    "유저" + i,
                    "user" + i + "@multi.com",
                    100000
            );
            userRepository.save(user);
            users.add(user);
        }

        // When: 50명이 3개 쿠폰을 동시에 발급 (총 150개 발급 시도)
        ExecutorService executorService = Executors.newFixedThreadPool(150);
        CountDownLatch latch = new CountDownLatch(150);

        AtomicInteger successCount = new AtomicInteger(0);

        for (User user : users) {
            for (Coupon coupon : coupons) {
                executorService.submit(() -> {
                    try {
                        IssueCouponCommand command = new IssueCouponCommand(user.getId(), coupon.getId());
                        couponService.issueCoupon(command);
                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        // 예외는 무시 (정상 동작)
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        latch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 150개 모두 성공 (각 쿠폰당 50명 발급)
        assertThat(successCount.get()).isEqualTo(150);

        // 각 쿠폰의 발급 수량 확인
        for (Coupon coupon : coupons) {
            Coupon updated = couponRepository.findById(coupon.getId()).orElseThrow();
            assertThat(updated.getIssuedQuantity()).isEqualTo(50);
        }
    }

    @Test
    @DisplayName("동시성 테스트: 중복 발급 방지 - 같은 유저가 같은 쿠폰을 동시에 발급받으면 1개만 성공")
    void concurrency_SameUser_SameCoupon_OnlyOneSuccess() throws InterruptedException {
        // Given: 쿠폰 생성
        Instant now = Instant.now();
        Coupon coupon = Coupon.create(
                "DUPLICATE_TEST_" + System.currentTimeMillis(),
                "중복 방지 쿠폰",
                DiscountType.FIXED,
                5000,
                100,
                0,
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                now.minusSeconds(3600),
                now.plusSeconds(86400)
        );
        couponRepository.save(coupon);

        // 유저 생성
        User user = User.createWithId(2000L, "중복테스트유저", "dup@test.com", 100000);
        userRepository.save(user);

        // When: 같은 유저가 같은 쿠폰을 10번 동시에 발급 시도
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            executorService.submit(() -> {
                try {
                    IssueCouponCommand command = new IssueCouponCommand(user.getId(), coupon.getId());
                    couponService.issueCoupon(command);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getMessage().contains("이미 발급")) {
                        duplicateCount.incrementAndGet();
                    } else {
                        throw e;
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 1개만 성공, 9개는 중복 에러
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(9);

        // 쿠폰 발급 수량 확인
        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("성능 테스트: 1000명이 선착순 500장 쿠폰을 동시 발급 - 5초 이내 완료")
    void performance_1000Users_500Coupons_Within5Seconds() throws InterruptedException {
        // Given: 선착순 500장 쿠폰
        Instant now = Instant.now();
        Coupon coupon = Coupon.create(
                "PERF_TEST_" + System.currentTimeMillis(),
                "성능 테스트 쿠폰",
                DiscountType.FIXED,
                5000,
                500,
                0,
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                now.minusSeconds(3600),
                now.plusSeconds(86400)
        );
        couponRepository.save(coupon);

        // 1000명의 유저 생성
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            User user = User.createWithId(
                    (long) (3000 + i),
                    "성능유저" + i,
                    "perf" + i + "@test.com",
                    100000
            );
            userRepository.save(user);
            users.add(user);
        }

        // When: 1000명이 동시에 쿠폰 발급 시도
        long startTime = System.currentTimeMillis();

        ExecutorService executorService = Executors.newFixedThreadPool(200);
        CountDownLatch latch = new CountDownLatch(1000);

        AtomicInteger successCount = new AtomicInteger(0);

        for (User user : users) {
            executorService.submit(() -> {
                try {
                    IssueCouponCommand command = new IssueCouponCommand(user.getId(), coupon.getId());
                    couponService.issueCoupon(command);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    // 실패는 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;

        // Then: 정확히 500명 성공
        assertThat(successCount.get()).isEqualTo(500);

        // 5초 이내 완료
        assertThat(elapsedTime).isLessThan(5000);

        System.out.println("1000명 동시 발급 소요 시간: " + elapsedTime + "ms");

        // 쿠폰 발급 수량 확인
        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(500);
    }
}

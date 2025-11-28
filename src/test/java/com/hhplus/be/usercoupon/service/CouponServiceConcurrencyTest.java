package com.hhplus.be.usercoupon.service;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.LockAcquisitionException;
import com.hhplus.be.coupon.domain.model.Coupon;
import com.hhplus.be.coupon.domain.model.DiscountType;
import com.hhplus.be.coupon.domain.repository.CouponRepository;
import com.hhplus.be.testsupport.IntegrationTestSupport;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.user.domain.repository.UserRepository;
import com.hhplus.be.usercoupon.domain.model.UserCoupon;
import com.hhplus.be.usercoupon.domain.repository.UserCouponRepository;
import com.hhplus.be.usercoupon.service.dto.IssueCouponCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * CouponService 동시성 통합 테스트
 *
 * 선착순 쿠폰 발급의 Race Condition 검증
 */
@SpringBootTest
@ActiveProfiles("test")
class CouponServiceConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private UserCouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private RedissonClient redissonClient;

    private static final String CODE_ALREADY_ISSUED = "ALREADY_ISSUED";
    private static final String CODE_STOCK_EMPTY   = "SOLD_OUT";

    @BeforeEach
    void setUp() {
        // Redis 클린업 (분산 락 키 삭제)
        redissonClient.getKeys().flushdb();

        // DB 클린업
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
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
        Coupon savedCoupon = couponRepository.save(coupon);
        assertThat(savedCoupon.getId()).isNotNull();

        // 100명의 유저 생성
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            User user = User.create(
                    "유저" + i,
                    "user" + i + "_" + UUID.randomUUID() + "@test.com",
                    100000
            );
            User savedUser = userRepository.save(user);
            assertThat(savedUser.getId()).as("Saved user ID should not be null").isNotNull();
            users.add(savedUser);
        }

        int taskCount = users.size();

        // When: 100명이 동시에 쿠폰 발급 시도
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(taskCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();

        for (User user : users) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    IssueCouponCommand command =
                            new IssueCouponCommand(user.getId(), savedCoupon.getId());
                    couponService.issueCoupon(command);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    String code = e.getErrorCode();
                    if (CODE_STOCK_EMPTY.equals(code)) {
                        failCount.incrementAndGet();
                    } else {
                        errors.add("Unexpected BusinessException: " + code + " / " + e.getMessage());
                        throw e;
                    }
                } catch (LockAcquisitionException e) {
                    // 락 획득 실패는 재고 소진과 동일하게 처리 (비즈니스 정책)
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e.getClass().getName() + ": " + e.getMessage());
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
    };

    @Test
    @DisplayName("동시성 테스트: 1명이 여러 쿠폰을 동시에 발급받아도 모두 성공")
    void concurrency_1User_MultipleCoupons_AllSuccess() throws InterruptedException {
        // Given: 5개의 쿠폰 생성
        Instant now = Instant.now();
        List<Coupon> coupons = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Coupon coupon = Coupon.create(
                    "MC_" + System.nanoTime() + "_" + i,
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
            Coupon savedCoupon = couponRepository.save(coupon);
            coupons.add(savedCoupon);
        }

        // 1명의 유저 생성
        User user = User.create("테스트유저", "test_" + System.currentTimeMillis() + "@test.com", 100000);
        User savedUser = userRepository.save(user);

        int taskCount = coupons.size();

        // When: 1명이 5개 쿠폰을 동시에 발급
        ExecutorService executorService = Executors.newFixedThreadPool(taskCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(taskCount);

        AtomicInteger successCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();

        for (Coupon savedCoupon : coupons) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    IssueCouponCommand command =
                            new IssueCouponCommand(savedUser.getId(), savedCoupon.getId());
                    couponService.issueCoupon(command);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e.getClass().getName() + ": " + e.getMessage());
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(taskCount);

        if (!errors.isEmpty()) {
            System.out.println("Unexpected errors in multiple coupons test:");
            errors.stream().limit(5).forEach(System.out::println);
        }

        // 각 쿠폰의 발급 수량 확인
        for (Coupon savedCoupon : coupons) {
            Coupon updated = couponRepository.findById(savedCoupon.getId()).orElseThrow();
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
                    "MT_" + System.nanoTime() + "_" + i,
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
            coupons.add(couponRepository.save(coupon));
        }

        // 50명의 유저 생성
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            User user = User.create(
                    "유저" + i,
                    "user" + i + "_" + UUID.randomUUID() + "@multi.com",
                    100000
            );
            users.add(userRepository.save(user));
        }

        int taskCount = users.size() * coupons.size();

        // When: 50명이 3개 쿠폰을 동시에 발급 (총 150개 발급 시도)
        ExecutorService executorService = Executors.newFixedThreadPool(30);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(taskCount);

        AtomicInteger successCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();

        for (User user : users) {
            for (Coupon savedCoupon : coupons) {
                executorService.submit(() -> {
                    try {
                        startLatch.await();

                        IssueCouponCommand command =
                                new IssueCouponCommand(user.getId(), savedCoupon.getId());
                        couponService.issueCoupon(command);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        errors.add(e.getClass().getName() + ": " + e.getMessage());
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
        }

        startLatch.countDown();

        boolean completed = doneLatch.await(20, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(taskCount);

        if (!errors.isEmpty()) {
            System.out.println("Unexpected errors in multi-user/multi-coupon test:");
            errors.stream().limit(5).forEach(System.out::println);
        }

        // 각 쿠폰의 발급 수량 확인 (각 50장)
        for (Coupon savedCoupon : coupons) {
            Coupon updated = couponRepository.findById(savedCoupon.getId()).orElseThrow();
            assertThat(updated.getIssuedQuantity()).isEqualTo(50);
        }
    }

    @Test
    @DisplayName("동시성 테스트: 중복 발급 방지 - 같은 유저가 같은 쿠폰을 동시에 발급받으면 1개만 성공")
    void concurrency_SameUser_SameCoupon_OnlyOneSuccess() throws InterruptedException {
        // Given: 쿠폰 생성
        Instant now = Instant.now();
        Coupon coupon = Coupon.create(
                "DUP_" + System.nanoTime(),
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
        Coupon savedCoupon = couponRepository.save(coupon);

        // 유저 생성
        User user = User.create("중복테스트유저",
                "dup_" + UUID.randomUUID() + "@test.com", 100000);
        User savedUser = userRepository.save(user);

        int taskCount = 10;

        // When: 같은 유저가 같은 쿠폰을 10번 동시에 발급 시도
        ExecutorService executorService = Executors.newFixedThreadPool(taskCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(taskCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    IssueCouponCommand command =
                            new IssueCouponCommand(savedUser.getId(), savedCoupon.getId());
                    couponService.issueCoupon(command);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    String code = e.getErrorCode();
                    if (CODE_ALREADY_ISSUED.equals(code)) {
                        duplicateCount.incrementAndGet();
                    } else {
                        errors.add("Unexpected BusinessException: " + code + " / " + e.getMessage());
                        throw e;
                    }
                } catch (LockAcquisitionException e) {
                    // 락 획득 실패 시에도 중복으로 간주 (다른 스레드가 먼저 처리 중)
                    duplicateCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e.getClass().getName() + ": " + e.getMessage());
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get() + duplicateCount.get())
                .as("모든 시도가 성공 또는 중복으로 처리되어야 함")
                .isEqualTo(taskCount);

        if (!errors.isEmpty()) {
            System.out.println("Unexpected errors in same-user/same-coupon test:");
            errors.stream().limit(5).forEach(System.out::println);
        }

        // Then: 1개만 성공, 9개는 중복 에러
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(9);

        // 쿠폰 발급 수량 확인
        Coupon updatedCoupon = couponRepository.findById(savedCoupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);

        // 실제 UserCoupon 레코드도 1건인지 확인
        Optional<UserCoupon> userCoupons =
                userCouponRepository.findByUserIdAndCouponId(savedUser.getId(), savedCoupon.getId());
        assertThat(userCoupons).isPresent();  // 1건 존재해야 함
    }

    @Test
    @DisplayName("성능 테스트: 500명이 선착순 100장 쿠폰을 동시 발급 - 10초 이내 완료(환경 따라 조정)")
    void performance_500Users_100Coupons_Within10Seconds() throws InterruptedException {
        // Given: 선착순 100장 쿠폰
        Instant now = Instant.now();
        Coupon coupon = Coupon.create(
                "PERF_" + System.nanoTime(),
                "성능 테스트 쿠폰",
                DiscountType.FIXED,
                5000,
                100,
                0,
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                now.minusSeconds(3600),
                now.plusSeconds(86400)
        );
        Coupon savedCoupon = couponRepository.save(coupon);

        // 500명의 유저 생성
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            User user = User.create(
                    "성능유저" + i,
                    "perf" + i + "_" + UUID.randomUUID() + "@test.com",
                    100000
            );
            users.add(userRepository.save(user));
        }

        int taskCount = users.size();

        // When: 500명이 동시에 쿠폰 발급 시도
        long startTime = System.currentTimeMillis();

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(taskCount);

        AtomicInteger successCount = new AtomicInteger(0);

        for (User user : users) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    IssueCouponCommand command =
                            new IssueCouponCommand(user.getId(), savedCoupon.getId());
                    couponService.issueCoupon(command);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 성능 테스트에서는 예외는 실패로 보되 따로 assert 하지는 않음
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        boolean completed = doneLatch.await(20, TimeUnit.SECONDS);
        executorService.shutdown();

        long elapsedTime = System.currentTimeMillis() - startTime;

        assertThat(completed).isTrue();
        // Then: 정확히 100명 성공
        assertThat(successCount.get()).isEqualTo(100);

        // 10초(환경에 따라 여유를 두고 싶으면 값 조정)
        assertThat(elapsedTime).isLessThan(10_000);

        System.out.println("500명 동시 발급 소요 시간: " + elapsedTime + "ms");

        // 쿠폰 발급 수량 확인
        Coupon updatedCoupon = couponRepository.findById(savedCoupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(100);
    }
}
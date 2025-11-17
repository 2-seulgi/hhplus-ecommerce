package com.hhplus.be.usercoupon.service;

import com.hhplus.be.coupon.domain.model.Coupon;
import com.hhplus.be.coupon.domain.model.DiscountType;
import com.hhplus.be.coupon.domain.repository.CouponRepository;
import com.hhplus.be.testsupport.IntegrationTestSupport;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.user.domain.repository.UserRepository;
import com.hhplus.be.usercoupon.domain.model.UserCoupon;
import com.hhplus.be.usercoupon.domain.repository.UserCouponRepository;
import com.hhplus.be.usercoupon.service.dto.GetUserCouponsQuery;
import com.hhplus.be.usercoupon.service.dto.GetUserCouponsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 사용자 쿠폰 Service 통합 테스트
 *
 * - N+1 문제: 각 UserCoupon마다 Coupon 정보 개별 조회
 * - 애플리케이션 레벨 필터링
 */
class UserCouponServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserCouponService userCouponService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    private User testUser;
    private List<Coupon> testCoupons;

    @BeforeEach
    void setUp() {
        // 테스트 유저 생성
        testUser = User.create(
                "쿠폰테스트유저",
                "coupon_test_" + System.currentTimeMillis() + "@test.com",
                100000
        );
        testUser = userRepository.save(testUser);

        // 테스트 쿠폰 10개 생성
        testCoupons = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 1; i <= 10; i++) {
            Coupon coupon = Coupon.create(
                    "COUPON_" + System.currentTimeMillis() + "_" + i,
                    "테스트 쿠폰 " + i,
                    DiscountType.FIXED,
                    5000 + (i * 1000),
                    100,
                    0,
                    now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS),
                    now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS)
            );
            testCoupons.add(couponRepository.save(coupon));
        }
    }

    @Test
    @DisplayName("사용자 쿠폰 조회 - N+1 문제 발생 확인")
    void getUserCoupons_N_Plus_1_Problem() {
        // Given: 사용자에게 10개 쿠폰 발급
        Instant now = Instant.now();
        for (Coupon coupon : testCoupons) {
            UserCoupon userCoupon = UserCoupon.create(testUser.getId(), coupon.getId(), now);
            userCouponRepository.save(userCoupon);
        }

        // When: 사용자 쿠폰 조회 (N+1 발생)
        GetUserCouponsQuery query = new GetUserCouponsQuery(testUser.getId(), null);
        GetUserCouponsResult result = userCouponService.getUserCoupons(query);

        // Then: 10개 쿠폰 정보 조회됨
        assertThat(result.coupons()).hasSize(10);

        // 각 쿠폰 정보가 제대로 조합되었는지 확인
        for (GetUserCouponsResult.UserCouponInfo info : result.coupons()) {
            assertThat(info.couponId()).isNotNull();
            assertThat(info.couponName()).isNotNull();
            assertThat(info.discountType()).isNotNull();
            assertThat(info.discountValue()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("사용자 쿠폰 조회 - 사용 가능한 쿠폰만 필터링")
    void getUserCoupons_OnlyAvailable() {
        // Given: 사용 가능/불가능 쿠폰 혼합
        Instant now = Instant.now();

        // 사용 가능한 쿠폰 5개
        for (int i = 0; i < 5; i++) {
            UserCoupon userCoupon = UserCoupon.create(testUser.getId(), testCoupons.get(i).getId(), now);
            userCouponRepository.save(userCoupon);
        }

        // 이미 사용한 쿠폰 3개
        for (int i = 5; i < 8; i++) {
            UserCoupon userCoupon = UserCoupon.create(testUser.getId(), testCoupons.get(i).getId(), now);
            userCoupon.use();
            userCouponRepository.save(userCoupon);
        }

        // 사용 기간이 지난 쿠폰 2개
        Coupon expiredCoupon1 = Coupon.create(
                "EXPIRED_1_" + System.currentTimeMillis(),
                "만료된 쿠폰 1",
                DiscountType.FIXED,
                5000,
                100,
                0,
                now.minus(30, ChronoUnit.DAYS),
                now.minus(10, ChronoUnit.DAYS),
                now.minus(30, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS) // 사용 기간 만료
        );
        Coupon savedExpiredCoupon = couponRepository.save(expiredCoupon1);
        UserCoupon expiredUserCoupon = UserCoupon.create(testUser.getId(), savedExpiredCoupon.getId(), now.minus(15, ChronoUnit.DAYS));
        userCouponRepository.save(expiredUserCoupon);

        // When: 사용 가능한 쿠폰만 조회
        GetUserCouponsQuery query = new GetUserCouponsQuery(testUser.getId(), true);
        GetUserCouponsResult result = userCouponService.getUserCoupons(query);

        // Then: 사용 가능한 5개만 반환
        assertThat(result.coupons()).hasSize(5);
        assertThat(result.coupons()).allMatch(c -> !c.used());
    }

    @Test
    @DisplayName("사용자 쿠폰 조회 - 전체 쿠폰 조회 (사용 여부 무관)")
    void getUserCoupons_All() {
        // Given: 사용 가능 5개 + 사용 완료 3개
        Instant now = Instant.now();

        for (int i = 0; i < 5; i++) {
            UserCoupon userCoupon = UserCoupon.create(testUser.getId(), testCoupons.get(i).getId(), now);
            userCouponRepository.save(userCoupon);
        }

        for (int i = 5; i < 8; i++) {
            UserCoupon userCoupon = UserCoupon.create(testUser.getId(), testCoupons.get(i).getId(), now);
            userCoupon.use();
            userCouponRepository.save(userCoupon);
        }

        // When: 전체 쿠폰 조회
        GetUserCouponsQuery query = new GetUserCouponsQuery(testUser.getId(), null);
        GetUserCouponsResult result = userCouponService.getUserCoupons(query);

        // Then: 8개 전체 반환
        assertThat(result.coupons()).hasSize(8);
    }

    @Test
    @DisplayName("성능 테스트: 100개 쿠폰 조회 - N+1 문제로 인한 성능 저하 확인")
    void performance_getUserCoupons_100Coupons_N_Plus_1_Impact() {
        // Given: 사용자에게 100개 쿠폰 발급
        Instant now = Instant.now();
        List<Coupon> manyCoupons = new ArrayList<>();

        for (int i = 1; i <= 100; i++) {
            Coupon coupon = Coupon.create(
                    "MANY_" + System.currentTimeMillis() + "_" + i,
                    "쿠폰 " + i,
                    DiscountType.FIXED,
                    5000,
                    1000,
                    0,
                    now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS),
                    now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS)
            );
            manyCoupons.add(couponRepository.save(coupon));
        }

        for (Coupon coupon : manyCoupons) {
            UserCoupon userCoupon = UserCoupon.create(testUser.getId(), coupon.getId(), now);
            userCouponRepository.save(userCoupon);
        }

        // When: 사용자 쿠폰 조회 (시간 측정)
        long startTime = System.currentTimeMillis();
        GetUserCouponsQuery query = new GetUserCouponsQuery(testUser.getId(), null);
        GetUserCouponsResult result = userCouponService.getUserCoupons(query);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 100개 쿠폰 조회됨
        assertThat(result.coupons()).hasSize(100);

        // N+1 문제로 인해 느릴 수 있음 (101개 쿼리: 1개 UserCoupon 조회 + 100개 Coupon 개별 조회)
        System.out.println("100개 쿠폰 조회 소요 시간 (N+1 문제): " + elapsedTime + "ms");

        // 참고: N+1 해결 후에는 이 시간이 크게 단축되어야 함
    }

    @Test
    @DisplayName("성능 테스트: 500개 쿠폰 조회 - N+1 문제 영향 측정")
    void performance_getUserCoupons_500Coupons_MeasureN_Plus_1() {
        // Given: 사용자에게 500개 쿠폰 발급
        Instant now = Instant.now();
        List<Coupon> manyCoupons = new ArrayList<>();

        for (int i = 1; i <= 500; i++) {
            Coupon coupon = Coupon.create(
                    "LOAD_" + System.currentTimeMillis() + "_" + i,
                    "쿠폰 " + i,
                    DiscountType.FIXED,
                    5000,
                    1000,
                    0,
                    now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS),
                    now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS)
            );
            manyCoupons.add(couponRepository.save(coupon));
        }

        for (Coupon coupon : manyCoupons) {
            UserCoupon userCoupon = UserCoupon.create(testUser.getId(), coupon.getId(), now);
            userCouponRepository.save(userCoupon);
        }

        // When: 사용자 쿠폰 조회 (시간 측정)
        long startTime = System.currentTimeMillis();
        GetUserCouponsQuery query = new GetUserCouponsQuery(testUser.getId(), null);
        GetUserCouponsResult result = userCouponService.getUserCoupons(query);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 500개 쿠폰 조회됨
        assertThat(result.coupons()).hasSize(500);

        System.out.println("500개 쿠폰 조회 소요 시간 (N+1 문제): " + elapsedTime + "ms");
        System.out.println("예상 쿼리 수: 501개 (1 UserCoupon 조회 + 500 Coupon 개별 조회)");

        // N+1 해결 후 개선 목표: 1~2개 쿼리로 단축 (JOIN 또는 IN 절 사용)
    }

    @Test
    @DisplayName("애플리케이션 레벨 필터링 - DB 레벨로 이동 시 성능 개선 확인")
    void performance_ApplicationLevelFiltering_ShouldMoveToDatabase() {
        // Given: 사용 가능 50개 + 사용 불가 450개 쿠폰
        Instant now = Instant.now();

        // 사용 가능한 쿠폰 50개
        for (int i = 0; i < 50; i++) {
            Coupon coupon = Coupon.create(
                    "AVAILABLE_" + System.currentTimeMillis() + "_" + i,
                    "사용 가능 쿠폰 " + i,
                    DiscountType.FIXED,
                    5000,
                    100,
                    0,
                    now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS),
                    now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS)
            );
            Coupon savedCoupon = couponRepository.save(coupon);
            UserCoupon userCoupon = UserCoupon.create(testUser.getId(), savedCoupon.getId(), now);
            userCouponRepository.save(userCoupon);
        }

        // 사용 불가능한 쿠폰 450개 (이미 사용됨)
        for (int i = 0; i < 450; i++) {
            Coupon coupon = Coupon.create(
                    "USED_" + System.currentTimeMillis() + "_" + i,
                    "사용 완료 쿠폰 " + i,
                    DiscountType.FIXED,
                    5000,
                    100,
                    0,
                    now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS),
                    now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS)
            );
            Coupon savedCoupon = couponRepository.save(coupon);
            UserCoupon userCoupon = UserCoupon.create(testUser.getId(), savedCoupon.getId(), now);
            userCoupon.use();
            userCouponRepository.save(userCoupon);
        }

        // When: 사용 가능한 쿠폰만 조회 (애플리케이션 레벨 필터링)
        long startTime = System.currentTimeMillis();
        GetUserCouponsQuery query = new GetUserCouponsQuery(testUser.getId(), true);
        GetUserCouponsResult result = userCouponService.getUserCoupons(query);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 50개만 반환되지만, 500개 모두 조회 후 필터링
        assertThat(result.coupons()).hasSize(50);

        System.out.println("사용 가능 쿠폰 조회 소요 시간 (애플리케이션 필터링): " + elapsedTime + "ms");
        System.out.println("문제: 500개 모두 조회 후 애플리케이션에서 필터링");
        System.out.println("개선안: WHERE used=false AND NOW() BETWEEN use_start_at AND use_end_at 조건으로 DB 레벨 필터링");
    }
}
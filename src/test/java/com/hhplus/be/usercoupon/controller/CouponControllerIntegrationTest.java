package com.hhplus.be.usercoupon.controller;

import com.hhplus.be.coupon.domain.model.Coupon;
import com.hhplus.be.coupon.domain.model.DiscountType;
import com.hhplus.be.coupon.domain.repository.CouponRepository;
import com.hhplus.be.coupon.service.CouponQueueService;
import com.hhplus.be.testsupport.IntegrationTestSupport;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.user.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CouponController 통합 테스트
 *
 * 테스트 API:
 * - POST /api/v1/users/{userId}/coupons/{couponId}/issue
 * - GET /api/v1/users/{userId}/coupons/{couponId}/result
 */
@AutoConfigureMockMvc
class CouponControllerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponQueueService couponQueueService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private Clock clock;

    private User testUser;
    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        // 테스트 유저 생성
        testUser = User.create(
                "쿠폰테스트유저",
                "coupon_test_" + System.currentTimeMillis() + "@test.com",
                100000
        );
        testUser = userRepository.save(testUser);

        // 테스트 쿠폰 생성 (선착순 100명)
        Instant now = clock.instant();
        testCoupon = Coupon.create(
                "FIRST_COME_" + System.currentTimeMillis(),
                "선착순 테스트 쿠폰",
                DiscountType.FIXED,
                5000,
                100,
                0,
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS)
        );
        testCoupon = couponRepository.save(testCoupon);
    }

    @AfterEach
    void tearDown() {
        // Redis 데이터 정리
        String queueKey = "coupon:queue:" + testCoupon.getId();
        String streamKey = "coupon:stream:" + testCoupon.getId();
        String counterKey = "coupon:counter:" + testCoupon.getId();
        redisTemplate.delete(queueKey);
        redisTemplate.delete(streamKey);
        redisTemplate.delete(counterKey);

        Set<String> resultKeys = redisTemplate.keys("coupon:result:*");
        if (resultKeys != null && !resultKeys.isEmpty()) {
            redisTemplate.delete(resultKeys);
        }
    }

    @Test
    @DisplayName("POST /issue - 쿠폰 발급 요청 성공 (202 Accepted)")
    void issueCoupon_Success_Returns202Accepted() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.message").value(containsString("발급 요청이 접수되었습니다")));
    }

    @Test
    @DisplayName("POST /issue - 선착순 마감 (400 Bad Request)")
    void issueCoupon_SoldOut_Returns400BadRequest() throws Exception {
        // Given: 100명이 이미 큐에 등록됨
        for (long i = 1; i <= 100; i++) {
            couponQueueService.tryEnqueue(testCoupon.getId(), i);
        }

        // When: 101번째 사용자 요청
        Long newUserId = 101L;
        User newUser = User.create("새유저", "new_" + System.currentTimeMillis() + "@test.com", 100000);
        newUser = userRepository.save(newUser);

        // Then: 400 Bad Request
        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        newUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.position").isEmpty())
                .andExpect(jsonPath("$.message").value(containsString("선착순 마감")));
    }

    @Test
    @DisplayName("POST /issue - 중복 발급 방지 (400 Bad Request)")
    void issueCoupon_Duplicate_Returns400BadRequest() throws Exception {
        // Given: 이미 발급 요청함
        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        // When: 동일 사용자가 다시 요청
        // Then: 400 Bad Request
        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /result - 처리 중 상태 (202 Accepted, PROCESSING)")
    void getCouponIssueResult_Processing_Returns202Accepted() throws Exception {
        // Given: 발급 요청만 하고 결과는 아직 저장 안 됨
        couponQueueService.tryEnqueue(testCoupon.getId(), testUser.getId());

        // When & Then: PROCESSING 상태
        mockMvc.perform(get("/api/v1/users/{userId}/coupons/{couponId}/result",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(content().string("PROCESSING"));
    }

    @Test
    @DisplayName("GET /result - 성공 상태 (200 OK, SUCCESS)")
    void getCouponIssueResult_Success_Returns200OK() throws Exception {
        // Given: 발급 성공 결과 저장
        couponQueueService.saveResult(testUser.getId(), testCoupon.getId(), true);

        // When & Then: SUCCESS 상태
        mockMvc.perform(get("/api/v1/users/{userId}/coupons/{couponId}/result",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));
    }

    @Test
    @DisplayName("GET /result - 실패 상태 (200 OK, FAILED)")
    void getCouponIssueResult_Failed_Returns200OK() throws Exception {
        // Given: 발급 실패 결과 저장
        couponQueueService.saveResult(testUser.getId(), testCoupon.getId(), false);

        // When & Then: FAILED 상태
        mockMvc.perform(get("/api/v1/users/{userId}/coupons/{couponId}/result",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("FAILED"));
    }

    @Test
    @DisplayName("발급 요청 → 결과 조회 통합 시나리오")
    void integrationScenario_IssueAndCheckResult() throws Exception {
        // Step 1: 발급 요청 (202 Accepted)
        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.position").value(1));

        // Step 2: 즉시 결과 조회 (202 Accepted, PROCESSING)
        mockMvc.perform(get("/api/v1/users/{userId}/coupons/{couponId}/result",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(content().string("PROCESSING"));

        // Step 3: Worker가 처리 완료 (시뮬레이션)
        couponQueueService.saveResult(testUser.getId(), testCoupon.getId(), true);

        // Step 4: 결과 조회 (200 OK, SUCCESS)
        mockMvc.perform(get("/api/v1/users/{userId}/coupons/{couponId}/result",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));
    }

    @Test
    @DisplayName("여러 사용자 동시 발급 요청 - 순위 확인")
    void multipleUsers_IssueRequest_CorrectPositions() throws Exception {
        // Given: 3명의 사용자
        User user1 = userRepository.save(User.create("유저1", "user1_" + System.currentTimeMillis() + "@test.com", 100000));
        User user2 = userRepository.save(User.create("유저2", "user2_" + System.currentTimeMillis() + "@test.com", 100000));
        User user3 = userRepository.save(User.create("유저3", "user3_" + System.currentTimeMillis() + "@test.com", 100000));

        // When & Then: 순서대로 요청, 순위 확인
        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        user1.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.position").value(1));

        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        user2.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.position").value(2));

        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        user3.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.position").value(3));
    }

    @Test
    @DisplayName("소량 쿠폰 - 동적 큐 사이즈 확인")
    void smallCoupon_DynamicQueueSize_WorksCorrectly() throws Exception {
        // Given: totalQuantity = 10인 쿠폰
        Instant now = clock.instant();
        Coupon smallCoupon = Coupon.create(
                "SMALL_" + System.currentTimeMillis(),
                "소량 쿠폰",
                DiscountType.FIXED,
                5000,
                10, // 10명만
                0,
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS)
        );
        smallCoupon = couponRepository.save(smallCoupon);

        // When: 10명까지는 성공
        for (int i = 1; i <= 10; i++) {
            User user = userRepository.save(User.create(
                    "유저" + i,
                    "user" + i + "_" + System.currentTimeMillis() + "@test.com",
                    100000
            ));

            mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                            user.getId(), smallCoupon.getId())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.position").value(i));
        }

        // 11번째는 실패
        User user11 = userRepository.save(User.create(
                "유저11",
                "user11_" + System.currentTimeMillis() + "@test.com",
                100000
        ));

        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        user11.getId(), smallCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("선착순 마감")));

        // Cleanup
        redisTemplate.delete("coupon:queue:" + smallCoupon.getId());
        redisTemplate.delete("coupon:stream:" + smallCoupon.getId());
        redisTemplate.delete("coupon:counter:" + smallCoupon.getId());
    }
}
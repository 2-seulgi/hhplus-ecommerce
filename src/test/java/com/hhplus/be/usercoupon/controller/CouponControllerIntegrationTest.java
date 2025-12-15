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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CouponController 통합 테스트 (Redis Stream 기반)
 *
 * 테스트 시나리오:
 * 1. 발급 요청 → 항상 202 Accepted (Stream에 추가만)
 * 2. 결과 조회 → PROCESSING / SUCCESS / FAILED
 *
 * 선착순/중복 체크는 Worker에서 수행하므로 Controller 테스트에서 제외
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
        // 테스트 사용자 생성
        testUser = User.create("테스트유저", "test_" + System.currentTimeMillis() + "@test.com", 100000);
        testUser = userRepository.save(testUser);

        // 테스트 쿠폰 생성
        Instant now = clock.instant();
        Instant validUntil = now.plus(7, ChronoUnit.DAYS);
        testCoupon = Coupon.create(
                "TEST" + System.currentTimeMillis(),  // code
                "선착순 테스트 쿠폰",                   // name
                DiscountType.FIXED,                  // discountType
                1000,                                // discountValue
                100,                                 // totalQuantity
                0,                                   // issuedQuantity
                now,                                 // issueStartAt
                validUntil,                          // issueEndAt
                now,                                 // useStartAt
                validUntil                           // useEndAt
        );
        testCoupon = couponRepository.save(testCoupon);

        // Redis 초기화
        cleanupRedis();
    }

    @AfterEach
    void tearDown() {
        cleanupRedis();
    }

    private void cleanupRedis() {
        // Stream 정리
        if (testCoupon != null) {
            String streamKey = "coupon:stream:" + testCoupon.getId();
            redisTemplate.delete(streamKey);
        }

        // Result 정리
        Set<String> resultKeys = redisTemplate.keys("coupon:result:*");
        if (resultKeys != null && !resultKeys.isEmpty()) {
            redisTemplate.delete(resultKeys);
        }
    }

    @Test
    @DisplayName("POST /issue - 발급 요청 성공 (202 Accepted)")
    void issueCoupon_Success_Returns202Accepted() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("발급 요청이 접수되었습니다")))
                .andExpect(jsonPath("$.resultUrl").value(containsString("/result")));
    }

    @Test
    @DisplayName("POST /issue - 여러 사용자 발급 요청 모두 202 반환 (Worker가 선착순 처리)")
    void issueCoupon_MultipleUsers_AllAccepted() throws Exception {
        // Given: 3명의 사용자
        User user1 = User.create("유저1", "user1_" + System.currentTimeMillis() + "@test.com", 50000);
        User user2 = User.create("유저2", "user2_" + System.currentTimeMillis() + "@test.com", 50000);
        User user3 = User.create("유저3", "user3_" + System.currentTimeMillis() + "@test.com", 50000);
        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(user3);

        // When & Then: 모두 202 Accepted (Stream에 추가만)
        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        user1.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        user2.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/users/{userId}/coupons/{couponId}/issue",
                        user3.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("GET /result - 처리 중 상태 (202 Accepted, PROCESSING)")
    void getCouponIssueResult_Processing_Returns202Accepted() throws Exception {
        // Given: 발급 요청만 하고 결과는 저장 안 함
        couponQueueService.enqueue(testCoupon.getId(), testUser.getId());

        // When & Then: PROCESSING 상태
        mockMvc.perform(get("/api/v1/users/{userId}/coupons/{couponId}/result",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(content().string("PROCESSING"));
    }

    @Test
    @DisplayName("GET /result - 발급 성공 (200 OK, SUCCESS)")
    void getCouponIssueResult_Success_Returns200OK() throws Exception {
        // Given: 결과를 SUCCESS로 저장
        couponQueueService.saveResult(testUser.getId(), testCoupon.getId(), true);

        // When & Then
        mockMvc.perform(get("/api/v1/users/{userId}/coupons/{couponId}/result",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));
    }

    @Test
    @DisplayName("GET /result - 발급 실패 (200 OK, FAILED)")
    void getCouponIssueResult_Failed_Returns200OK() throws Exception {
        // Given: 결과를 FAILED로 저장
        couponQueueService.saveResult(testUser.getId(), testCoupon.getId(), false);

        // When & Then
        mockMvc.perform(get("/api/v1/users/{userId}/coupons/{couponId}/result",
                        testUser.getId(), testCoupon.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("FAILED"));
    }

    @Test
    @DisplayName("GET /coupons - 보유 쿠폰 조회 (200 OK)")
    void getUserCoupons_Success_Returns200OK() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users/{userId}/coupons", testUser.getId())
                        .param("available", "false")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons").isArray());
    }
}
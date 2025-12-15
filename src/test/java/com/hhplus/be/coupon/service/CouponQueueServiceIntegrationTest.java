package com.hhplus.be.coupon.service;

import com.hhplus.be.coupon.domain.model.Coupon;
import com.hhplus.be.coupon.domain.model.DiscountType;
import com.hhplus.be.coupon.domain.repository.CouponRepository;
import com.hhplus.be.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * CouponQueueService 통합 테스트 (Redis Stream 기반)
 *
 * 테스트 항목:
 * 1. Redis Stream 이벤트 발행
 * 2. 결과 저장/조회
 * 3. TTL 동작 확인
 * 4. 동시성 테스트
 */
class CouponQueueServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private CouponQueueService couponQueueService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private Clock clock;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        // 테스트 쿠폰 생성
        Instant now = clock.instant();
        Instant validUntil = now.plus(7, ChronoUnit.DAYS);
        testCoupon = Coupon.create(
                "TEST" + System.currentTimeMillis(),  // code
                "테스트 쿠폰",                          // name
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
        // Stream 키 정리
        String streamKey = "coupon:stream:" + (testCoupon != null ? testCoupon.getId() : "*");
        redisTemplate.delete(streamKey);

        // Result 키 정리 (패턴 매칭)
        Set<String> resultKeys = redisTemplate.keys("coupon:result:*");
        if (resultKeys != null && !resultKeys.isEmpty()) {
            redisTemplate.delete(resultKeys);
        }
    }

    @Test
    @DisplayName("발급 요청 시 Redis Stream에 메시지가 추가된다")
    void enqueue_AddsMessageToStream() {
        // Given
        Long userId = 1L;

        // When
        couponQueueService.enqueue(testCoupon.getId(), userId);

        // Then: Stream에 메시지가 있는지 확인
        String streamKey = "coupon:stream:" + testCoupon.getId();
        Long streamSize = redisTemplate.opsForStream().size(streamKey);
        assertThat(streamSize).isEqualTo(1L);

        // 메시지 내용 확인
        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                .range(streamKey, org.springframework.data.domain.Range.unbounded());

        assertThat(messages).hasSize(1);
        MapRecord<String, Object, Object> message = messages.get(0);
        assertThat(message.getValue().get("userId")).isEqualTo(String.valueOf(userId));
        assertThat(message.getValue().get("couponId")).isEqualTo(String.valueOf(testCoupon.getId()));
    }

    @Test
    @DisplayName("여러 사용자의 발급 요청이 Stream에 순서대로 추가된다")
    void enqueue_MultipleUsers_AddsInOrder() {
        // Given
        List<Long> userIds = List.of(1L, 2L, 3L, 4L, 5L);

        // When
        for (Long userId : userIds) {
            couponQueueService.enqueue(testCoupon.getId(), userId);
        }

        // Then
        String streamKey = "coupon:stream:" + testCoupon.getId();
        Long streamSize = redisTemplate.opsForStream().size(streamKey);
        assertThat(streamSize).isEqualTo(5L);

        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                .range(streamKey, org.springframework.data.domain.Range.unbounded());
        assertThat(messages).hasSize(5);
    }

    @Test
    @DisplayName("발급 결과를 저장하고 조회할 수 있다")
    void saveAndGetResult_Success() {
        // Given
        Long userId = 100L;
        Long couponId = testCoupon.getId();

        // When: 성공 결과 저장
        couponQueueService.saveResult(userId, couponId, true);

        // Then: 결과 조회
        String result = couponQueueService.getResult(userId, couponId);
        assertThat(result).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("발급 실패 결과를 저장하고 조회할 수 있다")
    void saveAndGetResult_Failed() {
        // Given
        Long userId = 101L;
        Long couponId = testCoupon.getId();

        // When: 실패 결과 저장
        couponQueueService.saveResult(userId, couponId, false);

        // Then: 결과 조회
        String result = couponQueueService.getResult(userId, couponId);
        assertThat(result).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("처리되지 않은 요청은 null을 반환한다")
    void getResult_NotProcessed_ReturnsNull() {
        // Given: 저장하지 않음
        Long userId = 999L;
        Long couponId = testCoupon.getId();

        // When
        String result = couponQueueService.getResult(userId, couponId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("동시에 여러 사용자가 발급 요청을 해도 모두 Stream에 추가된다")
    void enqueue_ConcurrentRequests_AllAdded() throws InterruptedException {
        // Given
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When: 50명이 동시에 요청
        for (int i = 0; i < threadCount; i++) {
            final long userId = i;
            executor.submit(() -> {
                try {
                    couponQueueService.enqueue(testCoupon.getId(), userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 50개 메시지가 모두 Stream에 추가됨
        String streamKey = "coupon:stream:" + testCoupon.getId();
        Long streamSize = redisTemplate.opsForStream().size(streamKey);
        assertThat(streamSize).isEqualTo(50L);
    }

    @Test
    @DisplayName("같은 사용자가 여러 번 요청해도 Stream에 모두 추가된다 (중복 체크는 Worker에서)")
    void enqueue_SameUserMultipleTimes_AllAdded() {
        // Given
        Long userId = 1L;

        // When: 같은 사용자가 3번 요청
        couponQueueService.enqueue(testCoupon.getId(), userId);
        couponQueueService.enqueue(testCoupon.getId(), userId);
        couponQueueService.enqueue(testCoupon.getId(), userId);

        // Then: 3개 모두 Stream에 추가됨 (중복 체크는 Worker 담당)
        String streamKey = "coupon:stream:" + testCoupon.getId();
        Long streamSize = redisTemplate.opsForStream().size(streamKey);
        assertThat(streamSize).isEqualTo(3L);
    }
}
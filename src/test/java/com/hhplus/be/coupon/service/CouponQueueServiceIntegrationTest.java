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
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.*;

/**
 * CouponQueueService 통합 테스트
 *
 * 테스트 항목:
 * 1. 동적 큐 사이즈 (Coupon.totalQuantity)
 * 2. Redis Sorted Set 순위 관리
 * 3. Redis Stream 이벤트 발행
 * 4. 결과 저장/조회
 * 5. 동시성 테스트 (선착순 보장)
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
        // 테스트 쿠폰 생성 (선착순 100명)
        Instant now = clock.instant();
        testCoupon = Coupon.create(
                "FIRST_COME_" + System.currentTimeMillis(),
                "선착순 테스트 쿠폰",
                DiscountType.FIXED,
                5000,
                100, // totalQuantity = 100명
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

        // 결과 키 정리 (패턴 매칭)
        Set<String> resultKeys = redisTemplate.keys("coupon:result:*");
        if (resultKeys != null && !resultKeys.isEmpty()) {
            redisTemplate.delete(resultKeys);
        }
    }

    @Test
    @DisplayName("선착순 발급 성공 - 순위 확인")
    void tryEnqueue_Success_ReturnsPosition() {
        // Given
        Long userId = 1L;

        // When
        Long position = couponQueueService.tryEnqueue(testCoupon.getId(), userId);

        // Then
        assertThat(position).isEqualTo(1L); // 1등

        // Redis Sorted Set 확인
        String queueKey = "coupon:queue:" + testCoupon.getId();
        Long rank = redisTemplate.opsForZSet().rank(queueKey, String.valueOf(userId));
        assertThat(rank).isEqualTo(0L); // 0-based index
    }

    @Test
    @DisplayName("선착순 발급 - 순서대로 순위 부여")
    void tryEnqueue_MultipleUsers_CorrectPositions() {
        // Given: 10명의 사용자
        List<Long> userIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

        // When: 순차적으로 발급 요청
        List<Long> positions = new ArrayList<>();
        for (Long userId : userIds) {
            Long position = couponQueueService.tryEnqueue(testCoupon.getId(), userId);
            positions.add(position);
        }

        // Then: 순위가 1부터 10까지 순서대로
        assertThat(positions).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    @Test
    @DisplayName("선착순 마감 - 발급 수량 초과 시 null 반환")
    void tryEnqueue_ExceedLimit_ReturnsNull() {
        // Given: totalQuantity = 100인 쿠폰
        // 100명의 사용자가 이미 큐에 등록됨
        for (long i = 1; i <= 100; i++) {
            couponQueueService.tryEnqueue(testCoupon.getId(), i);
        }

        // When: 101번째 사용자 요청
        Long position = couponQueueService.tryEnqueue(testCoupon.getId(), 101L);

        // Then: null 반환 (선착순 마감)
        assertThat(position).isNull();

        // Redis Sorted Set에서 제거됨
        String queueKey = "coupon:queue:" + testCoupon.getId();
        Long rank = redisTemplate.opsForZSet().rank(queueKey, "101");
        assertThat(rank).isNull();
    }

    @Test
    @DisplayName("동적 큐 사이즈 - Coupon.totalQuantity 사용")
    void tryEnqueue_DynamicQueueSize_UsesTotalQuantity() {
        // Given: totalQuantity = 50인 쿠폰
        Instant now = clock.instant();
        Coupon smallCoupon = Coupon.create(
                "SMALL_" + System.currentTimeMillis(),
                "소량 쿠폰",
                DiscountType.FIXED,
                5000,
                50, // totalQuantity = 50명
                0,
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS)
        );
        smallCoupon = couponRepository.save(smallCoupon);

        // When: 1-50명까지는 성공
        for (long i = 1; i <= 50; i++) {
            Long position = couponQueueService.tryEnqueue(smallCoupon.getId(), i);
            assertThat(position).isNotNull();
        }

        // 51번째는 실패
        Long position51 = couponQueueService.tryEnqueue(smallCoupon.getId(), 51L);
        assertThat(position51).isNull();

        // Cleanup
        redisTemplate.delete("coupon:queue:" + smallCoupon.getId());
        redisTemplate.delete("coupon:stream:" + smallCoupon.getId());
        redisTemplate.delete("coupon:counter:" + smallCoupon.getId());
    }

    @Test
    @DisplayName("중복 발급 방지 - 이미 큐에 있는 사용자")
    void tryEnqueue_DuplicateUser_ReturnsNull() {
        // Given
        Long userId = 1L;
        couponQueueService.tryEnqueue(testCoupon.getId(), userId);

        // When: 동일 사용자가 다시 요청
        Long position = couponQueueService.tryEnqueue(testCoupon.getId(), userId);

        // Then: null 반환 (중복 방지)
        assertThat(position).isNull();
    }

    @Test
    @DisplayName("Redis Stream 이벤트 발행 확인")
    void tryEnqueue_Success_PublishesToStream() throws InterruptedException {
        // Given
        Long userId = 1L;

        // When
        couponQueueService.tryEnqueue(testCoupon.getId(), userId);

        // Then: Redis Stream에 메시지 발행됨
        String streamKey = "coupon:stream:" + testCoupon.getId();

        // Stream 길이 확인 (메시지가 1개 있어야 함)
        Long streamLength = redisTemplate.opsForStream().size(streamKey);
        assertThat(streamLength).isGreaterThan(0L);
    }

    @Test
    @DisplayName("결과 저장 및 조회 - SUCCESS")
    void saveAndGetResult_Success() {
        // Given
        Long userId = 1L;
        Long couponId = testCoupon.getId();

        // When: 성공 결과 저장
        couponQueueService.saveResult(userId, couponId, true);

        // Then: SUCCESS 조회
        String result = couponQueueService.getResult(userId, couponId);
        assertThat(result).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("결과 저장 및 조회 - FAILED")
    void saveAndGetResult_Failed() {
        // Given
        Long userId = 1L;
        Long couponId = testCoupon.getId();

        // When: 실패 결과 저장
        couponQueueService.saveResult(userId, couponId, false);

        // Then: FAILED 조회
        String result = couponQueueService.getResult(userId, couponId);
        assertThat(result).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("결과 조회 - 처리 중 (null)")
    void getResult_Processing_ReturnsNull() {
        // Given: 결과가 아직 저장되지 않음
        Long userId = 1L;
        Long couponId = testCoupon.getId();

        // When: 결과 조회
        String result = couponQueueService.getResult(userId, couponId);

        // Then: null 반환 (처리 중)
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("큐 크기 조회")
    void getQueueSize_ReturnsCorrectSize() {
        // Given: 5명이 큐에 등록
        for (long i = 1; i <= 5; i++) {
            couponQueueService.tryEnqueue(testCoupon.getId(), i);
        }

        // When
        long queueSize = couponQueueService.getQueueSize(testCoupon.getId());

        // Then
        assertThat(queueSize).isEqualTo(5L);
    }

    @Test
    @DisplayName("동시성 테스트 - 200명이 동시에 요청, 정확히 100명만 성공")
    void tryEnqueue_Concurrency_Exactly100Succeed() throws InterruptedException {
        // Given: 200명의 사용자가 동시에 요청
        int totalUsers = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(totalUsers);
        ConcurrentHashMap<Long, Long> results = new ConcurrentHashMap<>();

        // When: 동시 요청
        for (long userId = 1; userId <= totalUsers; userId++) {
            long finalUserId = userId;
            executorService.submit(() -> {
                try {
                    Long position = couponQueueService.tryEnqueue(testCoupon.getId(), finalUserId);
                    if (position != null) {
                        results.put(finalUserId, position);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 정확히 100명만 성공 (Redis INCR로 원자성 보장)
        assertThat(results.size()).isEqualTo(100);

        // 순위가 1부터 100까지인지 확인
        List<Long> positions = new ArrayList<>(results.values());
        assertThat(positions).containsExactlyInAnyOrder(
                LongStream.rangeClosed(1, 100).boxed().toArray(Long[]::new)
        );
    }

    @Test
    @DisplayName("타임스탬프 기반 순위 - 먼저 요청한 사용자가 먼저")
    void tryEnqueue_TimestampBased_FirstComeFirstServed() throws InterruptedException {
        // Given: 3명이 시간차로 요청
        Long user1 = 1L;
        Long user2 = 2L;
        Long user3 = 3L;

        // When
        Long position1 = couponQueueService.tryEnqueue(testCoupon.getId(), user1);
        Thread.sleep(10); // 시간차
        Long position2 = couponQueueService.tryEnqueue(testCoupon.getId(), user2);
        Thread.sleep(10); // 시간차
        Long position3 = couponQueueService.tryEnqueue(testCoupon.getId(), user3);

        // Then: 순서대로 순위 부여
        assertThat(position1).isEqualTo(1L);
        assertThat(position2).isEqualTo(2L);
        assertThat(position3).isEqualTo(3L);

        // Redis Sorted Set에서 Score 확인
        String queueKey = "coupon:queue:" + testCoupon.getId();
        Double score1 = redisTemplate.opsForZSet().score(queueKey, String.valueOf(user1));
        Double score2 = redisTemplate.opsForZSet().score(queueKey, String.valueOf(user2));
        Double score3 = redisTemplate.opsForZSet().score(queueKey, String.valueOf(user3));

        assertThat(score1).isLessThan(score2);
        assertThat(score2).isLessThan(score3);
    }
}
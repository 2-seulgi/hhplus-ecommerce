package com.hhplus.be.coupon.service;

import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.coupon.domain.model.Coupon;
import com.hhplus.be.coupon.domain.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis를 활용한 쿠폰 발급 큐 관리 서비스
 *
 * 아키텍처:
 * 1. Sorted Set: 선착순 순위 관리 (Score = 타임스탬프)
 * 2. Redis Stream: 발급 이벤트 큐 (워커가 소비)
 * 3. Redis String: 결과 저장 (TTL 10분)
 *
 * Redis Keys:
 * - coupon:queue:{couponId}           : Sorted Set (선착순 순위)
 * - coupon:stream:{couponId}          : Redis Stream (이벤트 큐)
 * - coupon:result:{userId}:{couponId} : String (발급 결과, TTL 10분)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponQueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clock;
    private final CouponRepository couponRepository;

    private static final String QUEUE_KEY_PREFIX = "coupon:queue:";
    private static final String STREAM_KEY_PREFIX = "coupon:stream:";
    private static final String RESULT_KEY_PREFIX = "coupon:result:";
    private static final String COUNTER_KEY_PREFIX = "coupon:counter:";
    private static final int RESULT_TTL_MINUTES = 10;

    /**
     * 선착순 쿠폰 발급 요청 처리 (원자적 카운터 사용)
     *
     * 프로세스:
     * 1. 쿠폰 정보 조회 (총 발급 수량 확인)
     * 2. Redis INCR로 원자적 카운터 증가 (race condition 방지)
     * 3. 발급 수량 이내면 Sorted Set 추가 + Stream 발행
     * 4. 발급 수량 초과면 카운터 감소
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 순위 (1부터 시작, 실패 시 null)
     */
    public Long tryEnqueue(Long couponId, Long userId) {
        String queueKey = getQueueKey(couponId);
        String counterKey = getCounterKey(couponId);
        String member = String.valueOf(userId);
        long timestamp = clock.millis();

        try {
            // 1. 쿠폰 정보 조회 (총 발급 수량 확인)
            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new ResourceNotFoundException("쿠폰을 찾을 수 없습니다"));
            int maxQueueSize = coupon.getTotalQuantity();

            // 2. 중복 확인: 이미 큐에 있으면 실패
            Long existingRank = redisTemplate.opsForZSet().rank(queueKey, member);
            if (existingRank != null) {
                log.warn("이미 큐에 있는 사용자 - userId: {}, couponId: {}", userId, couponId);
                return null;
            }

            // 3. 원자적 카운터 증가 (INCR)
            Long position = redisTemplate.opsForValue().increment(counterKey);
            if (position == null) {
                log.error("INCR 실패 - userId: {}, couponId: {}", userId, couponId);
                return null;
            }

            // 4. 발급 수량 이내면 Sorted Set 추가 + Stream 발행
            if (position <= maxQueueSize) {
                redisTemplate.opsForZSet().add(queueKey, member, timestamp);
                publishToStream(couponId, userId);
                log.debug("큐 추가 성공 - userId: {}, couponId: {}, position: {}/{}", userId, couponId, position, maxQueueSize);
                return position;
            }

            // 5. 발급 수량 초과면 카운터 감소 (DECR)
            redisTemplate.opsForValue().decrement(counterKey);
            log.debug("선착순 마감 - userId: {}, couponId: {}, position: {}/{}", userId, couponId, position, maxQueueSize);
            return null;

        } catch (Exception e) {
            log.error("큐 추가 실패 - userId: {}, couponId: {}", userId, couponId, e);
            throw e;
        }
    }

    /**
     * Redis Stream에 발급 이벤트 발행
     *
     * Stream 구조:
     * - Key: coupon:stream:{couponId}
     * - Field: userId, couponId
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     */
    private void publishToStream(Long couponId, Long userId) {
        String streamKey = getStreamKey(couponId);

        Map<String, String> message = new HashMap<>();
        message.put("userId", String.valueOf(userId));
        message.put("couponId", String.valueOf(couponId));

        redisTemplate.opsForStream().add(streamKey, message);
        log.debug("Stream 이벤트 발행 - streamKey: {}, userId: {}", streamKey, userId);
    }

    /**
     * 발급 결과 저장
     *
     * Redis String으로 저장:
     * - Key: coupon:result:{userId}:{couponId}
     * - Value: SUCCESS | FAILED
     * - TTL: 10분
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @param success 성공 여부
     */
    public void saveResult(Long userId, Long couponId, boolean success) {
        String resultKey = getResultKey(userId, couponId);
        String value = success ? "SUCCESS" : "FAILED";

        redisTemplate.opsForValue().set(resultKey, value, RESULT_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("발급 결과 저장 - userId: {}, couponId: {}, result: {}", userId, couponId, value);
    }

    /**
     * 발급 결과 조회
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return "SUCCESS" | "FAILED" | null (처리 중)
     */
    public String getResult(Long userId, Long couponId) {
        String resultKey = getResultKey(userId, couponId);
        return redisTemplate.opsForValue().get(resultKey);
    }

    /**
     * 현재 큐 크기 조회
     *
     * @param couponId 쿠폰 ID
     * @return 큐에 대기 중인 사용자 수
     */
    public long getQueueSize(Long couponId) {
        String queueKey = getQueueKey(couponId);
        Long size = redisTemplate.opsForZSet().zCard(queueKey);
        return size != null ? size : 0;
    }

    // Redis Key 생성
    private String getQueueKey(Long couponId) {
        return QUEUE_KEY_PREFIX + couponId;
    }

    private String getStreamKey(Long couponId) {
        return STREAM_KEY_PREFIX + couponId;
    }

    private String getResultKey(Long userId, Long couponId) {
        return RESULT_KEY_PREFIX + userId + ":" + couponId;
    }

    private String getCounterKey(Long couponId) {
        return COUNTER_KEY_PREFIX + couponId;
    }
}
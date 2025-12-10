package com.hhplus.be.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis를 활용한 쿠폰 발급 큐 관리 서비스
 *
 * 개선된 아키텍처 (단순화):
 * 1. Redis Stream: 발급 요청 메시지만 적재 (XADD)
 * 2. Redis String: 결과 저장 (TTL 10분)
 * 3. Worker: 모든 검증 + 발급 수행
 *    - 선착순 검증 (DB 기반)
 *    - 중복 발급 검증 (DB 기반)
 *    - 실제 쿠폰 발급
 *
 * Redis Keys:
 * - coupon:stream:{couponId}          : Redis Stream (이벤트 큐)
 * - coupon:result:{userId}:{couponId} : String (발급 결과, TTL 10분)
 *
 * 장점:
 * - Redis 로직 단순화 (INCR, ZADD 불필요)
 * - 원자성 걱정 없음
 * - 비즈니스 로직이 Worker에 집중
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponQueueService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String STREAM_KEY_PREFIX = "coupon:stream:";
    private static final String RESULT_KEY_PREFIX = "coupon:result:";
    private static final int RESULT_TTL_MINUTES = 10;

    /**
     * 쿠폰 발급 요청을 Stream에 적재 (단순화)
     *
     * 프로세스:
     * 1. Redis Stream에 메시지만 추가 (XADD)
     * 2. 즉시 202 Accepted 반환
     * 3. Worker가 모든 검증 + 발급 수행
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     */
    public void enqueue(Long couponId, Long userId) {
        try {
            // Stream에 발급 요청 메시지 추가
            publishToStream(couponId, userId);
            log.info("쿠폰 발급 요청 큐 추가 - userId: {}, couponId: {}", userId, couponId);

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

    // Redis Key 생성
    private String getStreamKey(Long couponId) {
        return STREAM_KEY_PREFIX + couponId;
    }

    private String getResultKey(Long userId, Long couponId) {
        return RESULT_KEY_PREFIX + userId + ":" + couponId;
    }
}
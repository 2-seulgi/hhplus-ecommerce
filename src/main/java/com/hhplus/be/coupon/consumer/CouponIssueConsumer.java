package com.hhplus.be.coupon.consumer;

import com.hhplus.be.coupon.domain.model.Coupon;
import com.hhplus.be.coupon.domain.repository.CouponRepository;
import com.hhplus.be.coupon.service.CouponQueueService;
import com.hhplus.be.usercoupon.service.UserCouponService;
import com.hhplus.be.usercoupon.service.dto.IssueCouponCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Redis Stream 쿠폰 발급 Consumer (개선됨)
 *
 * 역할:
 * 1. Redis Stream에서 발급 요청 메시지 소비 (XREADGROUP)
 * 2. 선착순 검증 (DB 기반: issued < totalQuantity)
 * 3. 중복 발급 검증 (DB 기반: UserCoupon 유니크 제약조건)
 * 4. 실제 쿠폰 발급 (UserCouponService 호출)
 * 5. 결과를 Redis에 저장 (SUCCESS/FAILED)
 *
 * 처리 방식:
 * - @Scheduled로 주기적으로 Stream 폴링
 * - Consumer Group 사용 (수평 확장 가능)
 * - 실패 시 ACK 전송 (재시도 방지)
 *
 * 개선 포인트:
 * - Redis는 단순히 메시지 큐 역할만 수행
 * - 모든 비즈니스 로직이 Worker에 집중
 * - 원자성 걱정 없이 깔끔한 구조
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final RedisTemplate<String, String> redisTemplate;
    private final UserCouponService userCouponService;
    private final CouponQueueService couponQueueService;
    private final CouponRepository couponRepository;
    private final Clock clock;

    @Value("${coupon.stream.consumer-group:coupon-consumer-group}")
    private String consumerGroup;

    @Value("${coupon.stream.consumer-name:consumer-1}")
    private String consumerName;

    private static final int BATCH_SIZE = 10;

    /**
     * Redis Stream 폴링 (100ms마다 실행)
     *
     * XREADGROUP 명령:
     * - Consumer Group 방식 (여러 Consumer 가능)
     * - ACK 필수 (처리 완료 후 XACK)
     * - Pending List로 실패 추적
     */
    @Scheduled(fixedDelay = 100)
    public void consumeStream() {
        try {
            // 발급 기간 중인 쿠폰 목록 조회
            List<Coupon> activeCoupons = couponRepository.findAllByIssuePeriod(clock.instant());

            // 각 쿠폰의 Stream 처리
            for (Coupon coupon : activeCoupons) {
                String streamKey = "coupon:stream:" + coupon.getId();
                consumeFromStream(streamKey);
            }

        } catch (Exception e) {
            log.error("Stream 소비 중 오류 발생", e);
        }
    }

    /**
     * 특정 Stream에서 메시지 소비
     *
     * @param streamKey Stream 키
     */
    private void consumeFromStream(String streamKey) {
        try {
            // Consumer Group 생성 (없으면)
            createConsumerGroupIfNotExists(streamKey);

            // XREADGROUP으로 메시지 읽기
            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                    .read(
                            Consumer.from(consumerGroup, consumerName),
                            StreamReadOptions.empty().count(BATCH_SIZE).block(Duration.ofMillis(100)),
                            StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                    );

            if (messages == null || messages.isEmpty()) {
                return;
            }

            log.debug("Stream 메시지 수신 - count: {}", messages.size());

            // 각 메시지 처리
            for (MapRecord<String, Object, Object> message : messages) {
                processMessage(streamKey, message);
            }

        } catch (Exception e) {
            log.error("Stream 읽기 실패 - streamKey: {}", streamKey, e);
        }
    }

    /**
     * 메시지 처리 (개선: Worker가 모든 검증 수행)
     *
     * 프로세스:
     * 1. 선착순 검증 (DB 기반: issued < totalQuantity)
     * 2. 중복 발급 검증 (DB 기반: UserCoupon 존재 확인)
     * 3. 실제 쿠폰 발급 (UserCouponService 호출)
     * 4. 결과 저장 (SUCCESS/FAILED)
     *
     * @param streamKey Stream 키
     * @param message 메시지
     */
    private void processMessage(String streamKey, MapRecord<String, Object, Object> message) {
        RecordId messageId = message.getId();
        Long userId = null;
        Long couponId = null;

        try {
            // 메시지에서 userId, couponId 추출
            userId = Long.parseLong((String) message.getValue().get("userId"));
            couponId = Long.parseLong((String) message.getValue().get("couponId"));

            log.info("쿠폰 발급 처리 시작 - userId: {}, couponId: {}", userId, couponId);

            // 1. 쿠폰 정보 조회
            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));

            // 2. 선착순 검증 (DB 기반)
            if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
                log.warn("선착순 마감 - userId: {}, couponId: {}, issued: {}/{}",
                        userId, couponId, coupon.getIssuedQuantity(), coupon.getTotalQuantity());

                // 실패 결과 저장
                couponQueueService.saveResult(userId, couponId, false);

                // ACK 전송 (처리 완료)
                redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, messageId);
                return;
            }

            // 3. 중복 발급 검증은 UserCouponService에서 처리 (DB 유니크 제약조건)

            // 4. 실제 쿠폰 발급
            IssueCouponCommand command = new IssueCouponCommand(userId, couponId);
            userCouponService.issueCoupon(command);

            // 5. 성공 결과 저장
            couponQueueService.saveResult(userId, couponId, true);
            log.info("쿠폰 발급 성공 - userId: {}, couponId: {}", userId, couponId);

            // ACK 전송 (처리 완료)
            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, messageId);

        } catch (Exception e) {
            log.error("쿠폰 발급 실패 - userId: {}, couponId: {}", userId, couponId, e);

            // 실패 결과 저장
            if (userId != null && couponId != null) {
                couponQueueService.saveResult(userId, couponId, false);
            }

            // ACK 전송 (재시도 방지)
            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, messageId);
        }
    }

    /**
     * Consumer Group 생성 (없으면)
     *
     * @param streamKey Stream 키
     */
    private void createConsumerGroupIfNotExists(String streamKey) {
        try {
            // Consumer Group 정보 조회
            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(streamKey);

            // 이미 존재하면 리턴
            boolean exists = groups.stream()
                    .anyMatch(group -> consumerGroup.equals(group.groupName()));

            if (exists) {
                return;
            }

            // Consumer Group 생성
            redisTemplate.opsForStream().createGroup(streamKey, consumerGroup);
            log.info("Consumer Group 생성 - streamKey: {}, group: {}", streamKey, consumerGroup);

        } catch (Exception e) {
            // Stream이 없으면 생성 후 재시도
            log.debug("Consumer Group 생성 실패 (Stream 없음) - streamKey: {}", streamKey);
        }
    }
}
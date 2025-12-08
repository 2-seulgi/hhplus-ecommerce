package com.hhplus.be.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 실시간 상품 랭킹 서비스 (Redis Sorted Set 기반)
 *
 * 핵심 전략:
 * 1. Redis Sorted Set 사용 (ZINCRBY, ZREVRANGE)
 * 2. Atomic 연산으로 동시성 보장 (ZINCRBY는 원자적 실행)
 * 3. 일간/주간/전체 랭킹 분리 관리
 * 4. Clock 주입으로 테스트 가능성 확보
 *
 * Redis 키 전략:
 * - product:ranking:daily:2025-12-02  (일간)
 * - product:ranking:weekly:2025-W48   (주간, ISO 8601 week)
 * - product:ranking:all               (전체)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRankingService {
    private final RedisTemplate<String, String> redisTemplate;

    private static final String RANKING_KEY_PREFIX = "product:ranking";
    private static final String DAILY_KEY_PATTERN = RANKING_KEY_PREFIX + ":daily:";
    private static final String WEEKLY_KEY_PATTERN = RANKING_KEY_PREFIX + ":weekly:";
    private static final String ALL_KEY = RANKING_KEY_PREFIX + ":all";
    private final Clock clock;

    /**
     * 상품 판매량 증가 (Atomic)
     *
     * Redis ZINCRBY 명령어 사용:
     * - Atomic 연산 보장 (ZINCRBY는 원자적으로 실행됨)
     * - 존재하지 않으면 자동으로 추가하고 score 설정
     * - 이미 존재하면 score만 증가
     *
     * 네트워크 최적화:
     * - 개별 호출 방식 (3번의 Redis 명령)
     * - TODO: Pipeline으로 최적화 고려 (SessionCallback 직렬화 이슈 해결 후)
     *
     * @param productId 상품 ID
     * @param quantity 판매 수량
     */
    public void incrementSalesCount(Long productId, int quantity) {
        String productKey = String.valueOf(productId);
        LocalDate today = LocalDate.now(clock);

        String dailyKey = getDailyKey(today);
        String weeklyKey = getWeeklyKey(today);

        try {
            // 1. 일간 랭킹 증가
            redisTemplate.opsForZSet().incrementScore(dailyKey, productKey, quantity);

            // 2. 주간 랭킹 증가
            redisTemplate.opsForZSet().incrementScore(weeklyKey, productKey, quantity);

            // 3. 전체 랭킹 증가
            redisTemplate.opsForZSet().incrementScore(ALL_KEY, productKey, quantity);

            log.debug("랭킹 업데이트 성공 - productId: {}, quantity: {}", productId, quantity);

        } catch (Exception e) {
            log.error("랭킹 업데이트 실패 - productId: {}, quantity: {}", productId, quantity, e);
            throw e;
        }
    }

    /**
     * Top N 상품 조회 (일간)
     *
     * Redis ZREVRANGE 명령어:
     * - ZREVRANGE key start stop WITHSCORES
     * - score 내림차순으로 조회 (높은 판매량부터)
     *
     * @param topN 조회할 상위 개수
     * @return 상품 ID와 판매량 리스트
     */
    public List<RankingResult> getTopProductsDaily(int topN) {
        LocalDate today = LocalDate.now(clock);
        String dailyKey = getDailyKey(today);

        return getTopProducts(dailyKey, topN);
    }

    /**
     * Top N 상품 조회 (주간)
     */
    public List<RankingResult> getTopProductsWeekly(int topN) {
        LocalDate today = LocalDate.now(clock);
        String weeklyKey = getWeeklyKey(today);

        return getTopProducts(weeklyKey, topN);
    }

    /**
     * Top N 상품 조회 (전체)
     */
    public List<RankingResult> getTopProductsAll(int topN) {
        return getTopProducts(ALL_KEY, topN);
    }

    /**
     * 공통 Top N 조회 로직
     */
    private List<RankingResult> getTopProducts(String key, int topN) {
        try {
            // ZREVRANGE: score 내림차순으로 0부터 (topN-1)까지 조회
            Set<ZSetOperations.TypedTuple<String>> results =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, topN - 1);

            if (results == null || results.isEmpty()) {
                return List.of();
            }

            return results.stream()
                    .map(tuple -> new RankingResult(
                            Long.parseLong(tuple.getValue()),
                            tuple.getScore().intValue()
                    ))
                    .toList();

        } catch (Exception e) {
            log.error("랭킹 조회 실패 - key: {}, topN: {}", key, topN, e);
            throw e;
        }
    }

    /**
     * 일간 랭킹 키 생성
     * 예: product:ranking:daily:2025-12-02
     */
    private String getDailyKey(LocalDate date) {
        return DAILY_KEY_PATTERN + date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * 주간 랭킹 키 생성
     * 예: product:ranking:weekly:2025-W48
     */
    private String getWeeklyKey(LocalDate date) {
        WeekFields weekFields = WeekFields.of(Locale.KOREA);
        int weekNumber = date.get(weekFields.weekOfWeekBasedYear());
        int year = date.get(weekFields.weekBasedYear());
        return WEEKLY_KEY_PATTERN + year + "-W" + String.format("%02d", weekNumber);
    }

    /**
     * 랭킹 결과 DTO
     */
    public record RankingResult(
            Long productId,
            int salesCount
    ) {}
}
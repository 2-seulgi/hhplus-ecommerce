package com.hhplus.be.product.service;

import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


/**
 * 실시간 상품 랭킹 통합 테스트
 *
 * 검증 대상:
 * 1. 주문 완료 이벤트 → Redis 랭킹 업데이트 (비동기)
 * 2. 일간/주간/전체 랭킹 분리 관리
 * 3. 랭킹 조회 API 정확성
 */
class ProductRankingIntegrationTest extends IntegrationTestSupport {


    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private ProductRankingService productRankingService;

    @BeforeEach
    void setUp() {
        Assertions.assertNotNull(redisTemplate.getConnectionFactory());
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    @DisplayName("주문 완료 이벤트 발행 시 Redis 랭킹이 실시간으로 업데이트된다")
    void 주문완료시_Redis랭킹_실시간_업데이트() {
        // Given
        Long orderId = 1L;
        Long productId = 1L;
        Long userId = 100L;
        int quantity = 5;

        List<OrderConfirmedEvent.OrderItemInfo> items = List.of(
                new OrderConfirmedEvent.OrderItemInfo(
                        productId,      // 상품 ID
                        "테스트 상품",    // 상품명
                        quantity               // 수량
                )
        );
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                orderId,
                userId,
                items,
                Instant.now()
        );

        // When: TransactionTemplate으로 명시적 커밋 (AFTER_COMMIT 동작)
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(event);
            return null; // 트랜잭션 종료 시 자동 커밋 → AFTER_COMMIT 발생!
        });

        // 비동기 처리 대기
        // Clock 사용: 프로덕션 코드와 동일한 시간 기준 보장
        String today = LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dailyKey = "product:ranking:daily:" + today;

        // Then
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Double score = redisTemplate.opsForZSet()
                            .score(dailyKey, String.valueOf(productId));
                    assertThat(score).isNotNull();
                    assertThat(score.intValue()).isEqualTo(quantity);
                });
    }

    @Test
    @DisplayName("여러 상품 주문 시 판매량 순으로 랭킹이 정렬된다")
    void 여러_상품_주문시_판매량_순으로_정렬() {
        // Given: 상품 A(10개), B(5개), C(15개) 주문
        Long userId = 100L;

        // 상품 A: 10개 판매
        publishOrderEvent(1L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(1L, "상품A", 10)
        ));

        // 상품 B: 5개 판매
        publishOrderEvent(2L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(2L, "상품B", 5)
        ));

        // 상품 C: 15개 판매
        publishOrderEvent(3L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(3L, "상품C", 15)
        ));

        // Then: 순서는 C(15) > A(10) > B(5)
        String today = LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dailyKey = "product:ranking:daily:" + today;

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var rankings = redisTemplate.opsForZSet()
                            .reverseRangeWithScores(dailyKey, 0, -1);

                    assertThat(rankings).hasSize(3);

                    var rankingList = rankings.stream().toList();
                    // 1위: 상품 C (15개)
                    assertThat(rankingList.get(0).getValue()).isEqualTo("3");
                    assertThat(rankingList.get(0).getScore()).isEqualTo(15.0);

                    // 2위: 상품 A (10개)
                    assertThat(rankingList.get(1).getValue()).isEqualTo("1");
                    assertThat(rankingList.get(1).getScore()).isEqualTo(10.0);

                    // 3위: 상품 B (5개)
                    assertThat(rankingList.get(2).getValue()).isEqualTo("2");
                    assertThat(rankingList.get(2).getScore()).isEqualTo(5.0);
                });
    }

    @Test
    @DisplayName("동일 상품을 여러 번 주문하면 판매량이 누적된다")
    void 동일_상품_여러_주문시_누적() {
        // Given: 상품 1번을 3번 주문 (5개 + 3개 + 7개 = 15개)
        Long userId = 100L;
        Long productId = 1L;

        publishOrderEvent(1L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(productId, "상품1", 5)
        ));

        publishOrderEvent(2L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(productId, "상품1", 3)
        ));

        publishOrderEvent(3L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(productId, "상품1", 7)
        ));

        // Then: 총 15개로 누적
        String today = LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dailyKey = "product:ranking:daily:" + today;

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Double score = redisTemplate.opsForZSet()
                            .score(dailyKey, String.valueOf(productId));

                    assertThat(score).isNotNull();
                    assertThat(score.intValue()).isEqualTo(15);
                });
    }

    @Test
    @DisplayName("한 주문에 같은 상품 여러 개 주문 시 수량만큼 랭킹 점수가 증가한다")
    void 한_주문에_여러개_주문시_수량만큼_증가() {
        // Given: 상품 1번을 10개 주문
        Long userId = 100L;
        Long productId = 1L;
        int quantity = 10;

        publishOrderEvent(1L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(productId, "상품1", quantity)
        ));

        // Then: score가 정확히 10
        String today = LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dailyKey = "product:ranking:daily:" + today;

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Double score = redisTemplate.opsForZSet()
                            .score(dailyKey, String.valueOf(productId));

                    assertThat(score).isNotNull();
                    assertThat(score.intValue()).isEqualTo(quantity);
                });
    }

    @Test
    @DisplayName("Top N 일간 랭킹 조회가 정상 동작한다")
    void Top_N_일간_랭킹_조회() {
        // Given: 5개 상품 주문 (판매량: 10, 20, 15, 5, 25)
        Long userId = 100L;
        publishOrderEvent(1L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(1L, "상품1", 10)
        ));
        publishOrderEvent(2L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(2L, "상품2", 20)
        ));
        publishOrderEvent(3L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(3L, "상품3", 15)
        ));
        publishOrderEvent(4L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(4L, "상품4", 5)
        ));
        publishOrderEvent(5L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(5L, "상품5", 25)
        ));

        // When: Top 3 조회
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<ProductRankingService.RankingResult> top3 =
                            productRankingService.getTopProductsDaily(3);

                    // Then: 상위 3개만 조회 (5위:25, 2위:20, 3위:15)
                    assertThat(top3).hasSize(3);
                    assertThat(top3.get(0).productId()).isEqualTo(5L);
                    assertThat(top3.get(0).salesCount()).isEqualTo(25);

                    assertThat(top3.get(1).productId()).isEqualTo(2L);
                    assertThat(top3.get(1).salesCount()).isEqualTo(20);

                    assertThat(top3.get(2).productId()).isEqualTo(3L);
                    assertThat(top3.get(2).salesCount()).isEqualTo(15);
                });
    }

    @Test
    @DisplayName("일간/주간/전체 랭킹이 독립적으로 관리된다")
    void 일간_주간_전체_랭킹_독립_관리() {
        // Given: 상품 1번 주문
        Long userId = 100L;
        Long productId = 1L;
        int quantity = 10;

        publishOrderEvent(1L, userId, List.of(
                new OrderConfirmedEvent.OrderItemInfo(productId, "상품1", quantity)
        ));

        // When & Then: 3개 키 모두 업데이트됨
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // 1. 일간 랭킹
                    List<ProductRankingService.RankingResult> daily =
                            productRankingService.getTopProductsDaily(10);
                    assertThat(daily).hasSize(1);
                    assertThat(daily.get(0).productId()).isEqualTo(productId);
                    assertThat(daily.get(0).salesCount()).isEqualTo(quantity);

                    // 2. 주간 랭킹
                    List<ProductRankingService.RankingResult> weekly =
                            productRankingService.getTopProductsWeekly(10);
                    assertThat(weekly).hasSize(1);
                    assertThat(weekly.get(0).productId()).isEqualTo(productId);
                    assertThat(weekly.get(0).salesCount()).isEqualTo(quantity);

                    // 3. 전체 랭킹
                    List<ProductRankingService.RankingResult> all =
                            productRankingService.getTopProductsAll(10);
                    assertThat(all).hasSize(1);
                    assertThat(all.get(0).productId()).isEqualTo(productId);
                    assertThat(all.get(0).salesCount()).isEqualTo(quantity);
                });
    }

    /**
     * Helper: 주문 이벤트 발행 (TransactionTemplate으로 커밋 보장)
     */
    private void publishOrderEvent(Long orderId, Long userId,
                                     List<OrderConfirmedEvent.OrderItemInfo> items) {
        transactionTemplate.execute(status -> {
            OrderConfirmedEvent event = new OrderConfirmedEvent(
                    orderId,
                    userId,
                    items,
                    Instant.now()
            );
            eventPublisher.publishEvent(event);
            return null;
        });
    }

}
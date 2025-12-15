package com.hhplus.be.product.listener;

import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.product.service.ProductRankingService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 비동기 이벤트 처리 테스트
 *
 * 검증 항목:
 * - 주문 완료 이벤트 발행 시 랭킹 업데이트
 * - @Async 비동기 처리 확인
 * - Redis ZINCRBY 호출 확인
 * - 여러 상품 동시 처리
 */
@SpringBootTest
@ActiveProfiles("test")
class
ProductRankingEventListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @SpyBean
    private ProductRankingService productRankingService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private Clock clock;

    private static final String RANKING_KEY = "product:ranking:sales";

    @Test
    @DisplayName("주문 완료 이벤트 발행 시 상품 랭킹이 비동기로 업데이트된다")
    void handleOrderConfirmed_UpdatesRankingAsynchronously() {
        // Given: 주문 완료 이벤트
        Long productId = 1L;
        Integer quantity = 3;

        OrderConfirmedEvent event = new OrderConfirmedEvent(
                100L,  // orderId
                200L,  // userId
                List.of(new OrderConfirmedEvent.OrderItemInfo(productId, "상품A", quantity)),
                Instant.now(clock)
        );

        // Redis 초기화
        redisTemplate.delete(RANKING_KEY);

        // When: 이벤트 발행
        eventPublisher.publishEvent(event);

        // Then: 비동기 처리 대기 후 검증 (최대 5초)
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(productRankingService, times(1))
                            .incrementSalesCount(eq(productId), eq(quantity));

                    // Redis에서 실제 점수 확인
                    Double score = redisTemplate.opsForZSet().score(RANKING_KEY, productId.toString());
                    assertThat(score).isNotNull();
                    assertThat(score).isEqualTo(quantity.doubleValue());
                });
    }

    @Test
    @DisplayName("여러 상품이 포함된 주문도 모두 랭킹에 반영된다")
    void handleOrderConfirmed_MultipleProducts() {
        // Given: 3개 상품이 포함된 주문
        List<OrderConfirmedEvent.OrderItemInfo> items = List.of(
                new OrderConfirmedEvent.OrderItemInfo(10L, "상품1", 2),
                new OrderConfirmedEvent.OrderItemInfo(20L, "상품2", 5),
                new OrderConfirmedEvent.OrderItemInfo(30L, "상품3", 1)
        );

        OrderConfirmedEvent event = new OrderConfirmedEvent(
                101L,
                201L,
                items,
                Instant.now(clock)
        );

        // Redis 초기화
        redisTemplate.delete(RANKING_KEY);

        // When: 이벤트 발행
        eventPublisher.publishEvent(event);

        // Then: 모든 상품의 랭킹 업데이트 확인
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(productRankingService).incrementSalesCount(10L, 2);
                    verify(productRankingService).incrementSalesCount(20L, 5);
                    verify(productRankingService).incrementSalesCount(30L, 1);

                    // Redis 검증
                    Double score1 = redisTemplate.opsForZSet().score(RANKING_KEY, "10");
                    Double score2 = redisTemplate.opsForZSet().score(RANKING_KEY, "20");
                    Double score3 = redisTemplate.opsForZSet().score(RANKING_KEY, "30");

                    assertThat(score1).isEqualTo(2.0);
                    assertThat(score2).isEqualTo(5.0);
                    assertThat(score3).isEqualTo(1.0);
                });
    }

    @Test
    @DisplayName("같은 상품에 대한 연속 주문이 랭킹에 누적된다")
    void handleOrderConfirmed_AccumulatesSameProduct() {
        // Given: 같은 상품(productId=50)에 대한 2번의 주문
        Long productId = 50L;

        OrderConfirmedEvent event1 = new OrderConfirmedEvent(
                102L, 202L,
                List.of(new OrderConfirmedEvent.OrderItemInfo(productId, "상품X", 3)),
                Instant.now(clock)
        );

        OrderConfirmedEvent event2 = new OrderConfirmedEvent(
                103L, 203L,
                List.of(new OrderConfirmedEvent.OrderItemInfo(productId, "상품X", 7)),
                Instant.now(clock)
        );

        // Redis 초기화
        redisTemplate.delete(RANKING_KEY);

        // When: 2번 이벤트 발행
        eventPublisher.publishEvent(event1);
        eventPublisher.publishEvent(event2);

        // Then: 점수가 누적됨 (3 + 7 = 10)
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(productRankingService, times(2))
                            .incrementSalesCount(eq(productId), anyInt());

                    Double score = redisTemplate.opsForZSet().score(RANKING_KEY, productId.toString());
                    assertThat(score).isEqualTo(10.0);
                });
    }
}
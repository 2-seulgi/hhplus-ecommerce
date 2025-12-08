package com.hhplus.be.product.listener;

import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.product.service.ProductRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 상품 랭킹 이벤트 리스너 (비동기)
 *
 * 처리 전략:
 * 1. @TransactionalEventListener(AFTER_COMMIT)
 *    - 주문 트랜잭션이 커밋된 후에만 실행
 *    - 주문 실패 시 랭킹 업데이트 안 됨 (데이터 정합성 보장)
 *
 * 2. @Async
 *    - 별도 스레드에서 비동기 실행
 *    - 주문 응답 속도에 영향 없음
 *
 * 3. @Retryable
 *    - Redis 일시적 장애 시 자동 재시도
 *    - 최대 3회, 1초 간격
 *
 * 성능 최적화:
 * - 주문 처리: 200ms (동기)
 * - 랭킹 업데이트: 50ms (비동기, 백그라운드)
 * → 사용자는 200ms만 대기
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductRankingEventListener {
    private final ProductRankingService productRankingService;

    /**
     * 주문 완료 이벤트 처리
     *
     * 실행 시점: 주문 트랜잭션 커밋 후 (AFTER_COMMIT)
     * 실행 방식: 비동기 (@Async)
     * 재시도: Redis 오류 시 최대 3회
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000),
            retryFor = {RedisConnectionFailureException.class}
    )
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        log.debug("랭킹 업데이트 시작 - orderId: {}, items: {}",
                event.getOrderId(), event.getOrderItems().size());

        try {
            // 각 주문 항목에 대해 랭킹 업데이트
            for (OrderConfirmedEvent.OrderItemInfo item : event.getOrderItems()) {
                productRankingService.incrementSalesCount(
                        item.getProductId(),
                        item.getQuantity()
                );

                log.debug("상품 랭킹 업데이트 완료 - productId: {}, quantity: {}",
                        item.getProductId(), item.getQuantity());
            }

            log.debug("랭킹 업데이트 성공 - orderId: {}", event.getOrderId());

        } catch (Exception e) {
            // 실패해도 주문은 이미 완료됨
            // 재시도 로직이 동작하거나, 최종 실패 시 로그만 남김
            log.error("랭킹 업데이트 실패 - orderId: {}, userId: {}",
                    event.getOrderId(), event.getUserId(), e);
            throw e; // 재시도를 위해 예외 재전파
        }
    }
}
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
 * 주문 완료 이벤트를 수신하여 상품 랭킹을 업데이트합니다.
 * Redis 오류 시 최대 3회 재시도합니다.
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
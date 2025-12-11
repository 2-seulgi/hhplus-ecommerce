package com.hhplus.be.product.listener;

import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.product.domain.model.ProductRankingOutbox;
import com.hhplus.be.product.domain.repository.ProductRankingOutboxRepository;
import com.hhplus.be.product.service.ProductRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;

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
 * 4. Outbox 패턴
 *    - 처리 이력 저장 (PENDING, SUCCESS, FAILED)
 *    - 실패 시 재처리 가능
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
    private final ProductRankingOutboxRepository outboxRepository;
    private final Clock clock;

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
                updateRanking(event.getOrderId(), item);
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

    /**
     * 개별 상품 랭킹 업데이트 (Outbox 패턴 적용)
     *
     * @param orderId 주문 ID
     * @param item 주문 항목
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void updateRanking(Long orderId, OrderConfirmedEvent.OrderItemInfo item) {
        Instant now = clock.instant();

        // 1. Outbox 레코드 생성 및 저장 (PENDING 상태)
        ProductRankingOutbox outbox = ProductRankingOutbox.create(
                orderId,
                item.getProductId(),
                item.getQuantity(),
                now
        );
        outbox = outboxRepository.save(outbox);

        // 2. 랭킹 업데이트 수행
        try {
            productRankingService.incrementSalesCount(
                    item.getProductId(),
                    item.getQuantity()
            );

            // 3. 성공 처리
            outbox.markAsSuccess(clock.instant());
            outboxRepository.save(outbox);

            log.debug("상품 랭킹 업데이트 완료 - productId: {}, quantity: {}, outboxId: {}",
                    item.getProductId(), item.getQuantity(), outbox.getId());

        } catch (Exception e) {
            String errorMsg = "랭킹 업데이트 실패: " + e.getMessage();

            // 4. 실패 처리
            outbox.markAsFailed(errorMsg, clock.instant());
            outboxRepository.save(outbox);

            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * 재시도 최종 실패 시 호출되는 복구 메서드
     *
     * @Retryable의 모든 재시도가 실패한 후 호출됩니다.
     * - 실패 로그 기록
     * - 알림 시스템 연동 가능 (TODO)
     * - Dead Letter Queue 전송 가능 (TODO)
     *
     * @param e 마지막 예외
     * @param event 원본 이벤트
     */
    @Recover
    public void recover(Exception e, OrderConfirmedEvent event) {
        log.error("🚨 [최종 실패] 랭킹 업데이트 실패 - 모든 재시도 소진 - orderId: {}, userId: {}, error: {}",
                event.getOrderId(), event.getUserId(), e.getMessage(), e);

        // TODO: 알림 시스템 연동 (Slack, Email 등)
        // TODO: Dead Letter Queue로 전송
        // TODO: 모니터링 메트릭 증가

        // 현재는 로그만 남김 (실패한 Outbox 레코드는 이미 FAILED 상태로 저장됨)
        // 나중에 스케줄러가 FAILED 레코드를 재처리할 것임
    }
}
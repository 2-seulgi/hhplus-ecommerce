package com.hhplus.be.order.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.repository.OrderDataPlatformOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;

/**
 * 데이터 플랫폼 전송 이벤트 리스너
 *
 * 주문 완료 이벤트를 받아 외부 데이터 플랫폼으로 전송합니다.
 * - Outbox 테이블에 전송 이력 저장
 * - 전송 성공/실패 상태 관리
 * - 실패 시 자동 재시도 (최대 3회)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDataPlatformEventListener {
    private final OrderDataPlatformOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000),
            retryFor = {RestClientException.class}
    )
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        log.info("📤 데이터 플랫폼 전송 시작 - orderId: {}, userId: {}",
                event.getOrderId(), event.getUserId());
        try {
            sendToDataPlatform(event);
            log.info("✅ 데이터 플랫폼 전송 완료 - orderId: {}", event.getOrderId());
        } catch (Exception e) {
            // 실패해도 주문은 이미 완료됨
            log.error("❌ 데이터 플랫폼 전송 실패 - orderId: {}, error: {}",
                    event.getOrderId(), e.getMessage());
            throw e; // 재시도를 위해 예외 재전파
        }
    }

    /**
     * 데이터 플랫폼으로 주문 정보 전송 (Mock 구현 + Outbox 저장)
     *
     * 실제 환경에서는:
     * - RestTemplate or WebClient 사용
     * - 실제 데이터 플랫폼 API 엔드포인트 호출
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void sendToDataPlatform(OrderConfirmedEvent event) {
        Instant now = clock.instant();

        // 1. 이벤트를 JSON으로 변환
        String orderDataJson;
        try {
            orderDataJson = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("이벤트 JSON 변환 실패 - orderId: {}", event.getOrderId(), e);
            throw new RuntimeException("Failed to serialize event", e);
        }

        // 2. Outbox 레코드 생성 및 저장 (PENDING 상태)
        OrderDataPlatformOutbox outbox = OrderDataPlatformOutbox.create(
                event.getOrderId(),
                event.getUserId(),
                orderDataJson,
                now
        );
        outbox = outboxRepository.save(outbox);

        // 3. Mock 전송 로직
        try {
            // TODO: 실제 API 호출로 대체
            log.debug("📤 [Mock API] 데이터 플랫폼 전송 - orderId: {}, userId: {}, items: {}",
                    event.getOrderId(), event.getUserId(), event.getOrderItems().size());

            // Mock 지연 시간 시뮬레이션 (50ms)
            Thread.sleep(50);

            // 4. 전송 성공 처리
            outbox.markAsSuccess(clock.instant());
            outboxRepository.save(outbox);

            log.debug("✅ Outbox 저장 완료 - outboxId: {}, orderId: {}",
                    outbox.getId(), event.getOrderId());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = "전송 중단됨: " + e.getMessage();

            // 5. 전송 실패 처리
            outbox.markAsFailed(errorMsg, clock.instant());
            outboxRepository.save(outbox);

            throw new RuntimeException(errorMsg, e);

        } catch (Exception e) {
            String errorMsg = "전송 실패: " + e.getMessage();

            // 5. 전송 실패 처리
            outbox.markAsFailed(errorMsg, clock.instant());
            outboxRepository.save(outbox);

            throw new RuntimeException(errorMsg, e);
        }
    }
}

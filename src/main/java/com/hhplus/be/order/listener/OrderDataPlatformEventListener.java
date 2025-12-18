package com.hhplus.be.order.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.repository.OrderDataPlatformOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
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

    @org.springframework.context.event.EventListener
    @Transactional
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        log.info("📤 데이터 플랫폼 전송 시작 - orderId: {}, userId: {}",
                event.getOrderId(), event.getUserId());


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
        outboxRepository.save(outbox);

        log.info("✅ Outbox 저장 완료 (PENDING) - orderId: {}, userId: {}",
                event.getOrderId(), event.getUserId());
        log.info("📌 Poller가 5초 이내에 Kafka로 발행할 예정");
    }
}

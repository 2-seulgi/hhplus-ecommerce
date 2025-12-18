package com.hhplus.be.order.listener;

import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.model.OutboxStatus;
import com.hhplus.be.order.domain.repository.OrderDataPlatformOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmedDltConsumer {

    private final OrderDataPlatformOutboxRepository outboxRepository;
    private final Clock clock;

    // 스케줄러와 동일한 재시도 한계 (일관성 유지)
    private static final int MAX_RETRY_COUNT = 10;

    @KafkaListener(
            topics = "order.confirmed.DLT",
            groupId = "ecommerce-order-dlt-consumer-group",
            containerFactory = "dltListenerContainerFactory"  // DLT 전용 factory 사용 (errorHandler 없음)
    )
    @Transactional
    public void consumeDlt(
            OrderConfirmedEvent event,
            Acknowledgment ack,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String exceptionClass,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer originalPartition,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset

    ) {
        try {
            Long orderId = event.getOrderId();

            log.error("🚨 [DLT] order.confirmed 최종 실패 - orderId={}, ex={} msg={} (from {}-{}@{})",
                    orderId, exceptionClass, exceptionMessage, originalTopic, originalPartition, originalOffset);

            OrderDataPlatformOutbox outbox = outboxRepository.findByOrderId(orderId)
                    .orElse(null);

            if (outbox == null) {
                // outbox가 없는 경우: 데이터 정합성 깨진 케이스라 로그 남기고 ACK
                log.error("🚨 [DLT] Outbox not found - orderId={}", orderId);
                ack.acknowledge();
                return;
            }

            // 이미 성공이면 무시(중복/경합 방지)
            if (outbox.getStatus() == OutboxStatus.SUCCESS) {
                ack.acknowledge();
                return;
            }

            // ⚠️ incrementRetry()는 스케줄러에서만 호출 (중복 방지)
            // DLT Consumer는 단순히 실패 상태만 기록

            // 재시도 한계 체크
            boolean isPermanentFailure = outbox.getRetryCount() >= MAX_RETRY_COUNT;

            String error = String.format(
                    "DLT: ex=%s msg=%s from=%s-%d@%d",
                    exceptionClass, exceptionMessage, originalTopic, originalPartition, originalOffset
            );

            if (isPermanentFailure) {
                log.error("💀 [DLT] 영구 실패 (재시도 한계 도달) - orderId={}, retryCount={}",
                        orderId, outbox.getRetryCount());
                error = "[PERMANENT_FAILURE] " + error;
            }

            outbox.markAsFailed(error, clock.instant());
            outboxRepository.save(outbox);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("❌ [DLT] 처리 실패 - orderId={}, error={}", event.getOrderId(), e.getMessage(), e);
            ack.acknowledge();
        }
    }
}

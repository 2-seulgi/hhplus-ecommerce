package com.hhplus.be.order.scheduler;

import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.model.OutboxStatus;
import com.hhplus.be.order.domain.repository.OrderDataPlatformOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러 기본 동작 테스트
 *
 * 검증 항목:
 * - FAILED 상태의 Outbox 재처리
 * - 재시도 횟수 증가
 * - 재시도 조건 (5분 경과, 10회 미만)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderDataPlatformOutboxRetrySchedulerTest {

    @Autowired
    private OrderDataPlatformOutboxRetryScheduler scheduler;

    @Autowired
    private OrderDataPlatformOutboxRepository outboxRepository;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("FAILED 상태의 Outbox를 재처리하여 SUCCESS로 변경한다")
    void retryFailedOutbox_Success() {
        // Given: 6분 전에 생성된 FAILED Outbox (5분 초과여야 재처리 대상)
        Instant sixMinutesAgo = clock.instant().minus(6, ChronoUnit.MINUTES);
        OrderDataPlatformOutbox outbox = OrderDataPlatformOutbox.create(
                1L,
                100L,
                "{\"orderId\":1}",
                sixMinutesAgo
        );
        outbox.markAsFailed("Mock 실패", sixMinutesAgo);
        outbox = outboxRepository.save(outbox);

        // When: 스케줄러 실행
        scheduler.retryFailedOutbox();

        // Then: SUCCESS로 변경됨
        OrderDataPlatformOutbox result = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.SUCCESS);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("재시도 횟수가 최대치(10회)에 도달한 Outbox는 재처리하지 않는다")
    void retryFailedOutbox_MaxRetryReached() {
        // Given: 재시도 횟수가 10회인 FAILED Outbox
        Instant fiveMinutesAgo = clock.instant().minus(5, ChronoUnit.MINUTES);
        OrderDataPlatformOutbox outbox = OrderDataPlatformOutbox.create(
                2L,
                200L,
                "{\"orderId\":2}",
                fiveMinutesAgo
        );

        // 재시도 횟수를 10회로 만들기
        for (int i = 0; i < 10; i++) {
            outbox.incrementRetry(fiveMinutesAgo);
        }
        outbox.markAsFailed("Max retry", fiveMinutesAgo);
        outbox = outboxRepository.save(outbox);

        Long outboxId = outbox.getId();
        int initialRetryCount = outbox.getRetryCount();

        // When: 스케줄러 실행
        scheduler.retryFailedOutbox();

        // Then: 상태 변경 없음 (재처리 스킵됨)
        OrderDataPlatformOutbox result = outboxRepository.findById(outboxId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(result.getRetryCount()).isEqualTo(initialRetryCount); // 증가하지 않음
    }

    @Test
    @DisplayName("생성 후 5분이 경과하지 않은 Outbox는 재처리하지 않는다")
    void retryFailedOutbox_NotEnoughTimeElapsed() {
        // Given: 1분 전에 생성된 FAILED Outbox (5분 미만)
        Instant oneMinuteAgo = clock.instant().minus(1, ChronoUnit.MINUTES);
        OrderDataPlatformOutbox outbox = OrderDataPlatformOutbox.create(
                3L,
                300L,
                "{\"orderId\":3}",
                oneMinuteAgo
        );
        outbox.markAsFailed("Recent failure", oneMinuteAgo);
        outbox = outboxRepository.save(outbox);

        Long outboxId = outbox.getId();

        // When: 스케줄러 실행
        scheduler.retryFailedOutbox();

        // Then: 상태 변경 없음 (재처리 스킵됨)
        OrderDataPlatformOutbox result = outboxRepository.findById(outboxId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(result.getRetryCount()).isEqualTo(0); // 증가하지 않음
    }

    @Test
    @DisplayName("SUCCESS 상태의 Outbox는 재처리하지 않는다")
    void retryFailedOutbox_SuccessStatusIgnored() {
        // Given: SUCCESS 상태의 Outbox
        Instant now = clock.instant();
        OrderDataPlatformOutbox outbox = OrderDataPlatformOutbox.create(
                4L,
                400L,
                "{\"orderId\":4}",
                now
        );
        outbox.markAsSuccess(now);
        outbox = outboxRepository.save(outbox);

        Long outboxId = outbox.getId();

        // When: 스케줄러 실행
        scheduler.retryFailedOutbox();

        // Then: 상태 변경 없음
        OrderDataPlatformOutbox result = outboxRepository.findById(outboxId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.SUCCESS);
        assertThat(result.getRetryCount()).isEqualTo(0);
    }
}
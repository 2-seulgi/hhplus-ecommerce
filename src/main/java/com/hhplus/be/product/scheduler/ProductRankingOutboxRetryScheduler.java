package com.hhplus.be.product.scheduler;

import com.hhplus.be.order.domain.model.OutboxStatus;
import com.hhplus.be.product.domain.model.ProductRankingOutbox;
import com.hhplus.be.product.domain.repository.ProductRankingOutboxRepository;
import com.hhplus.be.product.service.ProductRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 실패한 상품 랭킹 업데이트 재처리 스케줄러
 *
 * 역할:
 * - FAILED 상태의 Outbox 레코드를 주기적으로 조회
 * - 재처리 시도 (재시도 횟수 제한)
 * - 성공 시 SUCCESS 상태로 변경
 * - 실패 시 재시도 횟수 증가 및 FAILED 유지
 *
 * 실행 주기: 10분마다
 * 재시도 조건:
 * - 상태가 FAILED
 * - 재시도 횟수 < 10
 * - 생성 후 5분 경과 (즉시 재시도 방지)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductRankingOutboxRetryScheduler {
    private final ProductRankingOutboxRepository outboxRepository;
    private final ProductRankingService productRankingService;
    private final Clock clock;

    private static final int MAX_RETRY_COUNT = 10;
    private static final int RETRY_DELAY_MINUTES = 5;

    /**
     * 실패한 Outbox 재처리 (10분마다 실행)
     */
    @Scheduled(fixedDelay = 600_000) // 10분
    public void retryFailedOutbox() {
        try {
            log.debug("🔄 실패한 랭킹 업데이트 재처리 시작");

            // 재처리 대상 조회
            Instant retryBefore = clock.instant().minus(RETRY_DELAY_MINUTES, ChronoUnit.MINUTES);
            List<ProductRankingOutbox> failedOutboxes = outboxRepository
                    .findByStatusAndRetryCountLessThanAndCreatedAtBefore(
                            OutboxStatus.FAILED,
                            MAX_RETRY_COUNT,
                            retryBefore
                    );

            if (failedOutboxes.isEmpty()) {
                log.debug("재처리할 실패 레코드 없음");
                return;
            }

            log.info("재처리 대상 발견 - count: {}", failedOutboxes.size());

            // 각 실패 레코드 재처리
            int successCount = 0;
            int failCount = 0;

            for (ProductRankingOutbox outbox : failedOutboxes) {
                boolean success = retryOutbox(outbox);
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            log.info("✅ 재처리 완료 - 성공: {}, 실패: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("재처리 스케줄러 오류", e);
        }
    }

    /**
     * 개별 Outbox 재처리
     *
     * @param outbox 재처리할 Outbox
     * @return 성공 여부
     */
    @Transactional
    protected boolean retryOutbox(ProductRankingOutbox outbox) {
        try {
            log.debug("재처리 시도 - outboxId: {}, productId: {}, retryCount: {}",
                    outbox.getId(), outbox.getProductId(), outbox.getRetryCount());

            // 재시도 횟수 증가
            outbox.incrementRetry(clock.instant());
            outbox.markAsPending(clock.instant());
            outboxRepository.save(outbox);

            // 랭킹 업데이트 재시도
            productRankingService.incrementSalesCount(
                    outbox.getProductId(),
                    outbox.getQuantity()
            );

            // 성공 처리
            outbox.markAsSuccess(clock.instant());
            outboxRepository.save(outbox);

            log.info("✅ 재처리 성공 - outboxId: {}, productId: {}, retryCount: {}",
                    outbox.getId(), outbox.getProductId(), outbox.getRetryCount());

            return true;

        } catch (Exception e) {
            log.warn("❌ 재처리 실패 - outboxId: {}, productId: {}, retryCount: {}, error: {}",
                    outbox.getId(), outbox.getProductId(), outbox.getRetryCount(), e.getMessage());

            // 실패 처리
            String errorMsg = "재처리 실패 (시도 " + outbox.getRetryCount() + "회): " + e.getMessage();
            outbox.markAsFailed(errorMsg, clock.instant());
            outboxRepository.save(outbox);

            // 최대 재시도 횟수 도달 시 알림
            if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
                log.error("🚨 [최대 재시도 도달] 랭킹 업데이트 영구 실패 - outboxId: {}, productId: {}, orderId: {}",
                        outbox.getId(), outbox.getProductId(), outbox.getOrderId());
                // TODO: 알림 시스템 연동 (Slack, Email 등)
            }

            return false;
        }
    }
}
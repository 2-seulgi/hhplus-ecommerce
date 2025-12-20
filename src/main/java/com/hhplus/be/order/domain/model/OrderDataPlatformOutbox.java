package com.hhplus.be.order.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 데이터 플랫폼 전송 Outbox
 *
 * 주문 완료 정보를 외부 데이터 플랫폼으로 전송한 이력을 관리합니다.
 * - 전송 성공/실패 추적
 * - 재시도 횟수 관리
 * - 디버깅 및 모니터링 용도
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDataPlatformOutbox {

    private Long id;
    private Long orderId;
    private Long userId;
    private String orderData;      // JSON 형태로 저장
    private OutboxStatus status;
    private int retryCount;
    private Instant createdAt;
    private Instant sentAt;        // 전송 성공 시각 (nullable)
    private String errorMessage;   // 실패 시 에러 메시지 (nullable)
    private Instant updatedAt;

    /**
     * 신규 Outbox 레코드 생성 (PENDING 상태)
     */
    private OrderDataPlatformOutbox(Long orderId, Long userId, String orderData, Instant now) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (orderData == null || orderData.isBlank()) {
            throw new IllegalArgumentException("주문 데이터는 필수입니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다");
        }

        this.orderId = orderId;
        this.userId = userId;
        this.orderData = orderData;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static OrderDataPlatformOutbox create(Long orderId, Long userId, String orderData, Instant now) {
        return new OrderDataPlatformOutbox(orderId, userId, orderData, now);
    }

    /**
     * DB 복원용 팩토리
     */
    private OrderDataPlatformOutbox(Long id,
                                     Long orderId,
                                     Long userId,
                                     String orderData,
                                     OutboxStatus status,
                                     int retryCount,
                                     Instant createdAt,
                                     Instant sentAt,
                                     String errorMessage,
                                     Instant updatedAt) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (status == null) {
            throw new IllegalArgumentException("전송 상태는 필수입니다");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("생성 시각은 필수입니다");
        }

        this.id = id;
        this.orderId = orderId;
        this.userId = userId;
        this.orderData = orderData;
        this.status = status;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
        this.errorMessage = errorMessage;
        this.updatedAt = updatedAt != null ? updatedAt : createdAt;
    }

    public static OrderDataPlatformOutbox reconstruct(Long id,
                                                       Long orderId,
                                                       Long userId,
                                                       String orderData,
                                                       OutboxStatus status,
                                                       int retryCount,
                                                       Instant createdAt,
                                                       Instant sentAt,
                                                       String errorMessage,
                                                       Instant updatedAt) {
        return new OrderDataPlatformOutbox(
                id, orderId, userId, orderData, status, retryCount,
                createdAt, sentAt, errorMessage, updatedAt
        );
    }

    /**
     * Kafka 전송 시작 (PUBLISHING 상태로 변경)
     *
     * 목적: DB 트랜잭션과 Kafka 전송을 분리하기 위해
     * - PENDING → PUBLISHING: 전송 시작 표시 (짧은 트랜잭션)
     * - Kafka 전송 (트랜잭션 밖에서 네트워크 대기)
     * - PUBLISHING → PUBLISHED: 전송 성공 (짧은 트랜잭션)
     *
     * Stuck 방지:
     * - 프로세스가 전송 중 죽으면 PUBLISHING 상태로 남음
     * - Stuck PUBLISHING Scheduler가 timeout 기준으로 FAILED 처리
     */
    public void markAsPublishing(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다");
        }
        if (this.status != OutboxStatus.PENDING) {
            throw new IllegalStateException(
                "PENDING 상태에서만 PUBLISHING으로 변경 가능합니다. 현재 상태: " + this.status
            );
        }
        this.status = OutboxStatus.PUBLISHING;
        this.updatedAt = now;
    }

    /**
     * Kafka 발행 완료 처리
     */
    public void markAsPublished(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다");
        }
        if (this.status != OutboxStatus.PUBLISHING) {
            throw new IllegalStateException(
                "PUBLISHING 상태에서만 PUBLISHED로 변경 가능합니다. 현재 상태: " + this.status
            );
        }
        this.status = OutboxStatus.PUBLISHED;
        this.updatedAt = now;
        this.errorMessage = null; // 발행 성공 시 에러 메시지 초기화
    }

    /**
     * 전송 성공 처리 (Consumer가 처리 완료)
     */
    public void markAsSuccess(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다");
        }
        this.status = OutboxStatus.SUCCESS;
        this.sentAt = now;
        this.updatedAt = now;
        this.errorMessage = null; // 성공 시 에러 메시지 초기화
    }

    /**
     * 전송 실패 처리
     */
    public void markAsFailed(String errorMessage, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다");
        }
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = now;
    }

    /**
     * 재시도 횟수 증가
     */
    public void incrementRetry(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다");
        }
        this.retryCount++;
        this.updatedAt = now;
    }

    /**
     * 재처리 시작 시 PENDING으로 변경
     */
    public void markAsPending(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다");
        }
        this.status = OutboxStatus.PENDING;
        this.errorMessage = null;
        this.updatedAt = now;
    }
}

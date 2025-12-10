package com.hhplus.be.order.infrastructure.entity;

import com.hhplus.be.order.domain.model.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 데이터 플랫폼 전송 Outbox (JPA Entity)
 */
@Entity
@Table(
        name = "order_data_platform_outbox",
        indexes = {
                @Index(name = "idx_outbox_order_id", columnList = "orderId"),
                @Index(name = "idx_outbox_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OrderDataPlatformOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String orderData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant sentAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Instant updatedAt;
}
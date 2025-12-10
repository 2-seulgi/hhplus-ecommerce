package com.hhplus.be.order.infrastructure.repository;

import com.hhplus.be.order.domain.model.OutboxStatus;
import com.hhplus.be.order.infrastructure.entity.OrderDataPlatformOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderDataPlatformOutboxJpaRepository extends JpaRepository<OrderDataPlatformOutbox, Long> {
    Optional<OrderDataPlatformOutbox> findByOrderId(Long orderId);
    List<OrderDataPlatformOutbox> findByStatus(OutboxStatus status);
}
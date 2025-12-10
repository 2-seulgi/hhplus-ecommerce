package com.hhplus.be.order.infrastructure.repository;

import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.model.OutboxStatus;
import com.hhplus.be.order.domain.repository.OrderDataPlatformOutboxRepository;
import com.hhplus.be.order.infrastructure.mapper.OrderDataPlatformOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderDataPlatformOutboxRepositoryImpl implements OrderDataPlatformOutboxRepository {
    private final OrderDataPlatformOutboxJpaRepository jpaRepository;
    private final OrderDataPlatformOutboxMapper mapper;

    @Override
    public OrderDataPlatformOutbox save(OrderDataPlatformOutbox outbox) {
        var entity = mapper.toEntity(outbox);
        var savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<OrderDataPlatformOutbox> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<OrderDataPlatformOutbox> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId)
                .map(mapper::toDomain);
    }

    @Override
    public List<OrderDataPlatformOutbox> findByStatus(OutboxStatus status) {
        return jpaRepository.findByStatus(status).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
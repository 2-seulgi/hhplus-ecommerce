package com.hhplus.be.order.infrastructure.repository;

import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.infrastructure.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements com.hhplus.be.order.domain.repository.OrderRepository {
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @Override
    public Order save(Order order) {
        var entity = orderMapper.toEntity(order);
        var savedEntity = orderRepository.save(entity);
        return orderMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        return orderRepository.findById(orderId)
                .map(orderMapper::toDomain);
    }

    @Override
    public List<Order> findByUserIdOrderByCreatedAtDesc(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(orderMapper::toDomain)
                .toList();
    }

    @Override
    public List<Order> findAll() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteAll() {
        orderRepository.deleteAll();
    }
}

package com.hhplus.be.orderitem.infrastructure.repository;

import com.hhplus.be.orderitem.domain.model.OrderItem;
import com.hhplus.be.orderitem.domain.repository.OrderItemRepository;
import com.hhplus.be.orderitem.infrastructure.mapper.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OrderItemJpaImpl implements OrderItemRepository {
    private final OrderItemJpaRepository orderItemJpaRepository;
    private final OrderItemMapper orderItemMapper;

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        var jpaEntities = orderItems.stream()
                .map(orderItemMapper::toEntity)
                .toList();
        var savedEntities = orderItemJpaRepository.saveAll(jpaEntities);
        return savedEntities.stream()
                .map(orderItemMapper::toDomain)
                .toList();
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return orderItemJpaRepository.findByOrderId(orderId).stream()
                .map(orderItemMapper::toDomain)
                .toList();
    }

    @Override
    public List<OrderItem> findByOrderIdIn(List<Long> orderIds) {
        return orderItemJpaRepository.findByOrderIdIn(orderIds).stream()
                .map(orderItemMapper::toDomain)
                .toList();
    }

    @Override
    public Map<Long, Integer> countSalesByProductSince(Instant since) {
        return orderItemJpaRepository.countSalesByProductSince(since).stream()
                .collect(Collectors.toMap(
                        ProductSalesResult::productId,
                        ProductSalesResult::getTotalQuantity
                ));
    }

    @Override
    public void deleteAll() {
        orderItemJpaRepository.deleteAll();
    }
}

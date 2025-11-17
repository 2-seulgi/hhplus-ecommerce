package com.hhplus.be.orderitem.infrastructure.repository;

import com.hhplus.be.orderitem.domain.model.OrderItem;
import com.hhplus.be.orderitem.infrastructure.mapper.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OrderItemImpl implements com.hhplus.be.orderitem.domain.repository.OrderItemRepository {
    private final OrderItemRepository orderItemRepository;
    private final OrderItemMapper orderItemMapper;

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        var jpaEntities = orderItems.stream()
                .map(orderItemMapper::toEntity)
                .toList();
        var savedEntities = orderItemRepository.saveAll(jpaEntities);
        return savedEntities.stream()
                .map(orderItemMapper::toDomain)
                .toList();
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return orderItemRepository.findByOrderId(orderId).stream()
                .map(orderItemMapper::toDomain)
                .toList();
    }

    @Override
    public List<OrderItem> findByOrderIdIn(List<Long> orderIds) {
        return orderItemRepository.findByOrderIdIn(orderIds).stream()
                .map(orderItemMapper::toDomain)
                .toList();
    }

    @Override
    public Map<Long, Integer> countSalesByProductSince(Instant since) {
        return orderItemRepository.countSalesByProductSince(since).stream()
                .collect(Collectors.toMap(
                        ProductSalesResult::productId,
                        ProductSalesResult::getTotalQuantity
                ));
    }

    @Override
    public void deleteAll() {
        orderItemRepository.deleteAll();
    }
}

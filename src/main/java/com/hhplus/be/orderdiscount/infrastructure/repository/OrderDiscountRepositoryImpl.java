package com.hhplus.be.orderdiscount.infrastructure.repository;

import com.hhplus.be.orderdiscount.domain.OrderDiscount;
import com.hhplus.be.orderdiscount.domain.repository.OrderDiscountRepository;
import com.hhplus.be.orderdiscount.infrastructure.mapper.OrderDiscountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderDiscountRepositoryImpl implements OrderDiscountRepository {
    private final OrderDiscountJpaRepository orderDiscountJpaRepository;
    private final OrderDiscountMapper orderDiscountMapper;

    @Override
    public OrderDiscount save(OrderDiscount orderDiscount) {
        var entity = orderDiscountMapper.toEntity(orderDiscount);
        var savedEntity = orderDiscountJpaRepository.save(entity);
        return orderDiscountMapper.toDomain(savedEntity);
    }

    @Override
    public List<OrderDiscount> findByOrderId(Long orderId) {
        return orderDiscountJpaRepository.findByOrderId(orderId).stream()
                .map(orderDiscountMapper::toDomain)
                .toList();
    }
}
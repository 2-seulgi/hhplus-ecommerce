package com.hhplus.be.orderdiscount.infrastructure.repository;

import com.hhplus.be.orderdiscount.infrastructure.entity.OrderDiscountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderDiscountJpaRepository extends JpaRepository<OrderDiscountJpaEntity, Long> {
    List<OrderDiscountJpaEntity> findByOrderId(Long orderId);
}
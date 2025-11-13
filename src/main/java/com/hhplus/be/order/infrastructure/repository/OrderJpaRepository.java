package com.hhplus.be.order.infrastructure.repository;

import com.hhplus.be.order.infrastructure.entity.OrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {
    List<OrderJpaEntity>  findByUserIdOrderByCreatedAtDesc(Long userId);
}

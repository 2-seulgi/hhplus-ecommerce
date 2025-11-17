package com.hhplus.be.order.infrastructure.repository;

import com.hhplus.be.order.infrastructure.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order>  findByUserIdOrderByCreatedAtDesc(Long userId);
}

package com.hhplus.be.orderdiscount.infrastructure.repository;

import com.hhplus.be.orderdiscount.infrastructure.entity.OrderDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, Long> {
    List<OrderDiscount> findByOrderId(Long orderId);
}
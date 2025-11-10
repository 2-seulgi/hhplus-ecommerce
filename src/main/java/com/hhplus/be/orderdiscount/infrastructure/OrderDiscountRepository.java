package com.hhplus.be.orderdiscount.infrastructure;

import com.hhplus.be.orderdiscount.domain.OrderDiscount;

import java.util.List;

public interface OrderDiscountRepository {
    OrderDiscount save(OrderDiscount orderDiscount);

    List<OrderDiscount> findByOrderId(Long orderId);
}
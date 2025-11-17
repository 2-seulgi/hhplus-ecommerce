package com.hhplus.be.orderitem.infrastructure.repository;

import com.hhplus.be.orderitem.infrastructure.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    List<OrderItem> findByOrderIdIn(List<Long> orderIds);

    @Query("""
        SELECT new com.hhplus.be.orderitem.infrastructure.repository.ProductSalesResult(
            oi.productId,
            SUM(oi.quantity)
        )
        FROM OrderItem oi
        JOIN com.hhplus.be.order.infrastructure.entity.OrderJpaEntity o ON oi.orderId = o.id
        WHERE o.status = 'CONFIRMED'
        AND o.paidAt >= :since
        GROUP BY oi.productId
    """)
    List<ProductSalesResult> countSalesByProductSince(@Param("since") Instant since);
}

package com.hhplus.be.orderitem.infrastructure.repository;

import com.hhplus.be.orderitem.infrastructure.entity.OrderItemJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemJpaEntity, Long> {

    List<OrderItemJpaEntity> findByOrderId(Long orderId);

    List<OrderItemJpaEntity> findByOrderIdIn(List<Long> orderIds);

    @Query("""
        SELECT new com.hhplus.be.orderitem.infrastructure.repository.ProductSalesResult(
            oi.productId,
            SUM(oi.quantity)
        )
        FROM OrderItemJpaEntity oi
        JOIN com.hhplus.be.order.infrastructure.entity.OrderJpaEntity o ON oi.orderId = o.id
        WHERE o.status = 'CONFIRMED'
        AND o.paidAt >= :since
        GROUP BY oi.productId
    """)
    List<ProductSalesResult> countSalesByProductSince(@Param("since") Instant since);
}

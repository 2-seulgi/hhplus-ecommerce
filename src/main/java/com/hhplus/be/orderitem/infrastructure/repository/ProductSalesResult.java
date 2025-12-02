package com.hhplus.be.orderitem.infrastructure.repository;

public record ProductSalesResult(
        Long productId,
        String name,
        Integer price,
        Long totalQuantity
) {
    public Integer getSalesCount() {
        return totalQuantity.intValue();
    }
}
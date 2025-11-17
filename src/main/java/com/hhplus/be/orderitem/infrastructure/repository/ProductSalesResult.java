package com.hhplus.be.orderitem.infrastructure.repository;

public record ProductSalesResult(Long productId, Long totalQuantity) {

    public Integer getTotalQuantity() {
        return totalQuantity.intValue();
    }
}
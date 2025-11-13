package com.hhplus.be.order.domain.model;

public enum OrderStatus {
    PENDING,      // 결제 대기
    CONFIRMED,    // 결제 완료
    CANCELLED,    // 취소됨
    REFUNDED      // 환불됨
}

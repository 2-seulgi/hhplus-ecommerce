package com.hhplus.be.domain.coupon;

import com.hhplus.be.common.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType discountType;

    @Column(nullable = false)
    private int discountValue;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int issuedQuantity;

    @Version
    private int version;

    @Column(nullable = false)
    private Instant issueStartAt;

    @Column(nullable = false)
    private Instant issueEndAt;

    @Column(nullable = false)
    private Instant useStartAt;

    @Column(nullable = false)
    private Instant useEndAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Coupon(String code, String name, DiscountType discountType, int discountValue,
                   int totalQuantity, int issuedQuantity, Instant issueStartAt,
                   Instant issueEndAt, Instant useStartAt, Instant useEndAt) {
        this.code = code;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = issuedQuantity;
        this.version = 0;
        this.issueStartAt = issueStartAt;
        this.issueEndAt = issueEndAt;
        this.useStartAt = useStartAt;
        this.useEndAt = useEndAt;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    // 초기 데이터 생성을 위한 정적 팩토리 메서드
    public static Coupon create(String code, String name, DiscountType discountType,
                                int discountValue, int totalQuantity, int issuedQuantity,
                                Instant issueStartAt, Instant issueEndAt,
                                Instant useStartAt, Instant useEndAt) {
        return new Coupon(code, name, discountType, discountValue, totalQuantity,
                issuedQuantity, issueStartAt, issueEndAt, useStartAt, useEndAt);
    }

    // 발급 수량 증가 (낙관적 락으로 동시성 제어)
    public void increaseIssued() {
        if (!canIssue()) {
            throw new BusinessException("쿠폰이 모두 소진되었습니다", "SOLD_OUT");
        }
        this.issuedQuantity++;
        this.updatedAt = Instant.now();
    }

    // 발급 가능 여부
    public boolean canIssue() {
        return this.issuedQuantity < this.totalQuantity;
    }
}

package com.hhplus.be.coupon.domain.model;

import com.hhplus.be.common.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {
    private Long id;
    private String code;
    private String name;
    private DiscountType discountType;
    private int discountValue;
    private int totalQuantity;
    private int issuedQuantity;
    private int version;
    private Instant issueStartAt;
    private Instant issueEndAt;
    private Instant useStartAt;
    private Instant useEndAt;
    private Instant createdAt;
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

    // Mapper용 reconstruct 생성자
    private Coupon(Long id, String code, String name, DiscountType discountType,
                   int discountValue, int totalQuantity, int issuedQuantity, int version,
                   Instant issueStartAt, Instant issueEndAt, Instant useStartAt,
                   Instant useEndAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = issuedQuantity;
        this.version = version;
        this.issueStartAt = issueStartAt;
        this.issueEndAt = issueEndAt;
        this.useStartAt = useStartAt;
        this.useEndAt = useEndAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Coupon reconstruct(Long id, String code, String name, DiscountType discountType,
                                     int discountValue, int totalQuantity, int issuedQuantity,
                                     int version, Instant issueStartAt, Instant issueEndAt,
                                     Instant useStartAt, Instant useEndAt, Instant createdAt,
                                     Instant updatedAt) {
        return new Coupon(id, code, name, discountType, discountValue, totalQuantity,
                issuedQuantity, version, issueStartAt, issueEndAt, useStartAt, useEndAt,
                createdAt, updatedAt);
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

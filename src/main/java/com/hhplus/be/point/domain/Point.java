package com.hhplus.be.point.domain;

import com.hhplus.be.common.exception.InvalidInputException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "point")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Point {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointType pointType;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private int balanceAfter;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Point(Long userId, PointType pointType, int amount, int balanceAfter) {
        validateUserId(userId);
        validateAmount(amount);
        validateBalanceAfter(balanceAfter);

        this.userId = userId;
        this.pointType = pointType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = LocalDateTime.now();
    }

    public static Point charge(Long userId, int amount, int balanceAfter) {
        return new Point(userId, PointType.CHARGE, amount, balanceAfter);
    }

    public static Point use(Long userId, int amount, int balanceAfter) {
        return new Point(userId, PointType.USE, amount, balanceAfter);
    }

    public static Point refund(Long userId, int amount, int balanceAfter) {
        return new Point(userId, PointType.REFUND, amount, balanceAfter);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new InvalidInputException("사용자 ID는 필수입니다");
        }
    }

    private void validateAmount(int amount) {
        if (amount <= 0) {
            throw new InvalidInputException("금액은 양수여야 합니다");
        }
    }

    private void validateBalanceAfter(int balanceAfter) {
        if (balanceAfter < 0) {
            throw new InvalidInputException("거래 후 잔액은 0 이상이어야 합니다");
        }
    }

    /**
     * ID 할당 (Repository 전용 메서드)
     * JPA 도입 시 제거 예정
     *
     * WARNING: 비즈니스 로직에서 호출 금지!
     * Repository 구현체에서만 사용해야 합니다.
     */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID는 한 번만 할당할 수 있습니다");
        }
        this.id = id;
    }
}
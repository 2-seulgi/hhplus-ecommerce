package com.hhplus.be.domain.user;

import com.hhplus.be.common.exception.InsufficientBalanceException;
import com.hhplus.be.common.exception.InvalidInputException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private int balance;

    @Version
    private int version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // 사용자는 이미 등록되어 있다고 가정 (테스트/개발용 생성자)
    private User(String name, String email, int balance) {
        this.name = name;
        this.email = email;
        this.balance = balance;
        this.version = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // 테스트/초기 데이터 생성용
    public static User create(String name, String email, int initialBalance) {
        return new User(name, email, initialBalance);
    }

    public void charge(int amount) {
        if (amount <= 0) {
            throw new InvalidInputException("충전 금액은 양수여야 합니다");
        }
        this.balance += amount;
        this.updatedAt = Instant.now();
    }

    public void use(int amount) {
        if (amount <= 0) {
            throw new InvalidInputException("사용 금액은 양수여야 합니다");
        }
        if (this.balance < amount) {
            throw new InsufficientBalanceException("잔액이 부족합니다");
        }
        this.balance -= amount;
        this.updatedAt = Instant.now();
    }

    public void refund(int amount) {
        if (amount <= 0) {
            throw new InvalidInputException("환불 금액은 양수여야 합니다");
        }
        this.balance += amount;
        this.updatedAt = Instant.now();
    }

}
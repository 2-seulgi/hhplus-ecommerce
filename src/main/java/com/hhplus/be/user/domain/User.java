package com.hhplus.be.user.domain;

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

    private User(String name, String email, int balance) {
        this.name = name;
        this.email = email;
        this.balance = balance;
        this.version = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // 초기 데이터용 (ID 포함)
    public static User createWithId(Long id, String name, String email, int initialBalance) {
        User user = new User(name, email, initialBalance);
        user.id = id;
        return user;
    }

    public void charge(int amount) {
        if (amount <= 0) {
            throw new InvalidInputException("충전 금액은 양수여야 합니다");
        }
        if (amount < 1000) {
            throw new InvalidInputException("최소 충전 금액은 1000원입니다");
        }
        if (amount > 1000000) {
            throw new InvalidInputException("최대 충전 금액은 1,000,000원입니다");
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
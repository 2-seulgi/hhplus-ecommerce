package com.hhplus.be.user.domain.model;

import com.hhplus.be.common.exception.InsufficientBalanceException;
import com.hhplus.be.common.exception.InvalidInputException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    private Long id;
    private String name;
    private String email;
    private int balance;
    private int version;
    private Instant createdAt;
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
    public static User create(Long id, String name, String email, int initialBalance) {
        User user = new User(name, email, initialBalance);
        user.id = id;
        return user;
    }

    public static User reconstruct(Long id, String name, String email, int balance,
                                   int version, Instant createdAt, Instant updatedAt) {
        return new User(id, name, email, balance, version, createdAt, updatedAt);
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
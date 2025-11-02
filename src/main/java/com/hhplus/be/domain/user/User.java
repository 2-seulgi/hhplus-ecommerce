package com.hhplus.be.domain.user;

import com.hhplus.be.common.exception.InsufficientBalanceException;
import com.hhplus.be.common.exception.InvalidInputException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private User(String name, String email, int balance) {
        validateName(name);
        validateEmail(email);
        validateBalance(balance);

        this.name = name;
        this.email = email;
        this.balance = balance;
        this.version = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static User create(String name, String email, int initialBalance) {
        return new User(name, email, initialBalance);
    }

    public void charge(int amount) {
        if (amount <= 0) {
            throw new InvalidInputException("충전 금액은 양수여야 합니다");
        }
        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void use(int amount) {
        if (amount <= 0) {
            throw new InvalidInputException("사용 금액은 양수여야 합니다");
        }
        if (this.balance < amount) {
            throw new InsufficientBalanceException("잔액이 부족합니다");
        }
        this.balance -= amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void refund(int amount) {
        if (amount <= 0) {
            throw new InvalidInputException("환불 금액은 양수여야 합니다");
        }
        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidInputException("이름은 필수입니다");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new InvalidInputException("이메일은 필수입니다");
        }
    }

    private void validateBalance(int balance) {
        if (balance < 0) {
            throw new InvalidInputException("잔액은 0 이상이어야 합니다");
        }
    }
}
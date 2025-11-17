package com.hhplus.be.point.infrastructure.entity;

import com.hhplus.be.point.domain.model.PointType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "point")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
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
    private Instant createdAt;

    // Mapper용 생성자 (id 없음)
    public Point(Long userId, PointType pointType, int amount, int balanceAfter, Instant createdAt) {
        this.userId = userId;
        this.pointType = pointType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }
}
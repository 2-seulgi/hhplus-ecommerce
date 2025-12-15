package com.hhplus.be.config.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ShedLock 테이블 Entity
 *
 * 용도:
 * - 여러 서버 인스턴스 환경에서 스케줄러 중복 실행 방지
 * - ShedLock 라이브러리가 이 테이블을 사용하여 분산 락 관리
 *
 * 참고:
 * - 이 Entity는 직접 사용하지 않음 (ShedLock 라이브러리가 관리)
 * - JPA가 테이블만 생성하면 됨
 */
@Entity
@Table(name = "shedlock")
@Getter
@NoArgsConstructor
public class ShedlockEntity {

    /**
     * 스케줄러 작업 이름 (고유 식별자)
     */
    @Id
    @Column(name = "name", length = 64)
    private String name;

    /**
     * 락 만료 시각 (이 시각까지 락 유지)
     */
    @Column(name = "lock_until", nullable = false, columnDefinition = "TIMESTAMP(3)")
    private Instant lockUntil;

    /**
     * 락 획득 시각
     */
    @Column(name = "locked_at", nullable = false, columnDefinition = "TIMESTAMP(3)")
    private Instant lockedAt;

    /**
     * 락을 획득한 인스턴스 식별자 (hostname)
     */
    @Column(name = "locked_by", nullable = false)
    private String lockedBy;
}
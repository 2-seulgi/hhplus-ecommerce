package com.hhplus.be.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * ShedLock 설정
 *
 * 역할:
 * - 여러 서버 인스턴스 환경에서 @Scheduled 작업의 중복 실행 방지
 * - DB 테이블 기반 분산 락 제공
 *
 * 동작 방식:
 * - shedlock 테이블에 락 정보 저장
 * - lockAtMostFor: 최대 락 유지 시간 (서버 장애 대비)
 * - lockAtLeastFor: 최소 락 유지 시간 (너무 자주 실행 방지)
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
@org.springframework.context.annotation.Profile("!test")  // 테스트 환경에서는 비활성화
public class SchedulerLockConfig {

    /**
     * JDBC 기반 Lock Provider
     *
     * shedlock 테이블을 사용하여 분산 락 관리
     * - 테이블은 Flyway migration으로 생성됨
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
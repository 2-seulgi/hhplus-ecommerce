package com.hhplus.be.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * 테스트용 ShedLock 설정
 *
 * 차이점:
 * - @EnableScheduling 제외 → 자동 스케줄링 비활성화
 * - LockProvider만 제공 → 수동 호출 테스트 지원
 *
 * 이유:
 * - 테스트에서 스케줄러 자동 실행 시 비결정적 동작 발생
 * - scheduler.retryFailedOutbox() 수동 호출로 로직 검증
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
@Profile("test")
public class TestSchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
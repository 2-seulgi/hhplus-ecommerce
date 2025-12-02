package com.hhplus.be.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 기반 분산 락 어노테이션
 *
 * 메서드에 선언하면 해당 메서드 실행 전 분산 락을 획득하고,
 * 실행 후 자동으로 락을 해제합니다.
 *
 * Pub/Sub 방식으로 락 대기를 처리하여 효율적입니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락 키 (SpEL 지원)
     * 예: "stock:#productId" → "stock:123"
     */
    String key();

    /**
     * 락 획득을 기다리는 최대 시간
     * 이 시간 내에 락을 획득하지 못하면 예외 발생
     */
    long waitTime() default 5L;

    /**
     * 락을 보유하는 최대 시간
     * 이 시간이 지나면 자동으로 락 해제 (Watchdog가 없을 경우)
     */
    long leaseTime() default 3L;

    /**
     * 시간 단위
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
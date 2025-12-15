package com.hhplus.be.common.aop;

import com.hhplus.be.common.annotation.DistributedLock;
import com.hhplus.be.common.exception.LockAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;


@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedLockAop {
    private static final String REDISSON_LOCK_PREFIX = "LOCK:";
    private final RedissonClient redissonClient;
    private final AopForTransaction aopForTransaction;

    @Around("@annotation(distributedLock)")
    public Object lock(final ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        String dynamicKey = CustomSpringELParser.getDynamicValue(
                parameterNames,
                args,
                distributedLock.key()
        ).toString();

        String lockKey = REDISSON_LOCK_PREFIX + dynamicKey;
        RLock lock = redissonClient.getLock(lockKey);
        log.info("락 획득 시도: key={}", lockKey);

        try {
            boolean available = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );
            if (!available) {
                log.error("락 획득 실패: key={}", lockKey);
                throw new LockAcquisitionException("락 획득 실패: " + lockKey);
            }
            log.info("락 획득 성공: key={}",  lockKey);
            // 트랜잭션 분리하여 수행
            return aopForTransaction.proceed(joinPoint);
        }finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("락 해제 성공: key={}",  lockKey);
            }
        }
    }

}

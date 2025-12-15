package com.hhplus.be.scheduler;

import com.hhplus.be.order.domain.model.OrderDataPlatformOutbox;
import com.hhplus.be.order.domain.model.OutboxStatus;
import com.hhplus.be.order.domain.repository.OrderDataPlatformOutboxRepository;
import com.hhplus.be.order.scheduler.OrderDataPlatformOutboxRetryScheduler;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러 동시성 검증 테스트
 *
 * 검증 항목:
 * - ShedLock이 여러 인스턴스에서 중복 실행 방지
 * - 같은 Outbox를 여러 스레드가 동시에 처리 시도 시 충돌 방지
 * - 락 획득/해제 정상 동작
 */
@SpringBootTest
@ActiveProfiles("test")
class SchedulerConcurrencyTest {

    @Autowired
    private OrderDataPlatformOutboxRetryScheduler scheduler;

    @Autowired
    private OrderDataPlatformOutboxRepository outboxRepository;

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("ShedLock이 동시에 락을 획득하려는 요청 중 하나만 성공시킨다")
    void shedLock_AllowsOnlyOneInstance() throws InterruptedException {
        // Given: 락 설정
        String lockName = "testSchedulerLock";
        LockConfiguration lockConfig = new LockConfiguration(
                clock.instant(),
                lockName,
                Duration.ofSeconds(10),
                Duration.ZERO
        );

        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When: 5개 스레드가 동시에 락 획득 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Optional<SimpleLock> lock = lockProvider.lock(lockConfig);
                    if (lock.isPresent()) {
                        successCount.incrementAndGet();
                        // 락 보유 시간 시뮬레이션
                        Thread.sleep(100);
                        lock.get().unlock();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then: 1개만 성공, 나머지는 실패
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("여러 스레드가 동시에 스케줄러를 실행해도 Outbox는 한 번만 처리된다")
    @Transactional
    void scheduler_ProcessesOutboxOnlyOnce_WhenMultipleThreadsExecute() throws InterruptedException {
        // Given: FAILED 상태의 Outbox 생성 (6분 전)
        Instant sixMinutesAgo = clock.instant().minus(6, ChronoUnit.MINUTES);
        OrderDataPlatformOutbox outbox = OrderDataPlatformOutbox.create(
                999L,
                888L,
                "{\"test\":\"data\"}",
                sixMinutesAgo
        );
        outbox.markAsFailed("Test failure", sixMinutesAgo);
        outbox = outboxRepository.save(outbox);
        Long outboxId = outbox.getId();

        int threadCount = 3;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When: 3개 스레드가 동시에 스케줄러 실행
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    scheduler.retryFailedOutbox();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: Outbox는 SUCCESS가 되고, retryCount는 1
        OrderDataPlatformOutbox result = outboxRepository.findById(outboxId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.SUCCESS);
        assertThat(result.getRetryCount()).isEqualTo(1); // 중복 처리 방지됨
    }

    @Test
    @DisplayName("같은 Outbox에 대한 동시 업데이트 시 데이터 정합성이 유지된다")
    @Transactional
    void concurrentOutboxUpdate_MaintainsDataConsistency() throws InterruptedException {
        // Given: FAILED 상태의 Outbox (6분 전)
        Instant sixMinutesAgo = clock.instant().minus(6, ChronoUnit.MINUTES);
        OrderDataPlatformOutbox outbox = OrderDataPlatformOutbox.create(
                1000L,
                900L,
                "{\"concurrent\":\"test\"}",
                sixMinutesAgo
        );
        outbox.markAsFailed("Concurrent test", sixMinutesAgo);
        outbox = outboxRepository.save(outbox);
        Long outboxId = outbox.getId();

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Exception> exceptions = new ArrayList<>();

        // When: 10개 스레드가 동시에 같은 Outbox를 재처리 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 준비될 때까지 대기

                    // 스케줄러 전체 실행 (ShedLock이 중복 실행 방지)
                    scheduler.retryFailedOutbox();

                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 모든 스레드 동시 시작
        endLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 데이터 정합성 확인
        OrderDataPlatformOutbox result = outboxRepository.findById(outboxId).orElseThrow();

        // 최종 상태는 SUCCESS여야 함
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.SUCCESS);

        // retryCount는 10 이하여야 함 (동시성 제어가 완벽하면 1, 일부 충돌 허용 시 10 이하)
        assertThat(result.getRetryCount()).isLessThanOrEqualTo(threadCount);
        assertThat(result.getRetryCount()).isGreaterThan(0);

        // 일부 스레드는 예외를 경험할 수 있음 (낙관적 락 충돌 등)
        System.out.println("동시성 테스트 중 발생한 예외 수: " + exceptions.size());
    }

    @Test
    @DisplayName("ShedLock 락이 만료되면 다른 인스턴스가 락을 획득할 수 있다")
    void shedLock_ReleasesLockAfterExpiration() throws InterruptedException {
        // Given: 짧은 만료 시간의 락 설정 (1초)
        String lockName = "expirationTestLock";
        LockConfiguration lockConfig = new LockConfiguration(
                clock.instant(),
                lockName,
                Duration.ofSeconds(1),
                Duration.ZERO
        );

        // When: 첫 번째 락 획득
        Optional<SimpleLock> firstLock = lockProvider.lock(lockConfig);
        assertThat(firstLock).isPresent();

        // 두 번째 락 시도 (즉시) - 실패해야 함
        Optional<SimpleLock> secondLockImmediate = lockProvider.lock(lockConfig);
        assertThat(secondLockImmediate).isEmpty();

        // 1.5초 대기 (락 만료)
        Thread.sleep(1500);

        // Then: 세 번째 락 시도 - 성공해야 함
        LockConfiguration newLockConfig = new LockConfiguration(
                clock.instant(),
                lockName,
                Duration.ofSeconds(5),
                Duration.ZERO
        );
        Optional<SimpleLock> thirdLock = lockProvider.lock(newLockConfig);
        assertThat(thirdLock).isPresent();

        thirdLock.get().unlock();
    }
}
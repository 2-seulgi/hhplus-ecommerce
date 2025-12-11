# MSA 확장 시 트랜잭션 문제 분석 및 대응 방안 설계

## 📋 목차
1. [현재 아키텍처 분석](#1-현재-아키텍처-분석)
2. [MSA 전환 시나리오](#2-msa-전환-시나리오)
3. [분산 트랜잭션 문제점](#3-분산-트랜잭션-문제점)
4. [대응 방안: SAGA 패턴](#4-대응-방안-saga-패턴)
5. [Outbox 패턴 적용](#5-outbox-패턴-적용)
6. [실패 복구 전략](#6-실패-복구-전략)
7. [성능 및 일관성 트레이드오프](#7-성능-및-일관성-트레이드오프)

---

## 1. 현재 아키텍처 분석

### 1.1 모놀리식 구조

```
┌─────────────────────────────────────────────┐
│         Application (Single DB)              │
├─────────────────────────────────────────────┤
│  ProcessPaymentUseCase (Orchestrator)       │
│  ├─ OrderService                            │
│  ├─ ProductStockService                     │
│  ├─ PointService                            │
│  ├─ CouponService                           │
│  └─ ApplicationEventPublisher               │
├─────────────────────────────────────────────┤
│  Single PostgreSQL Database                 │
│  ├─ orders                                  │
│  ├─ products                                │
│  ├─ users (points)                          │
│  └─ coupons                                 │
└─────────────────────────────────────────────┘
```

### 1.2 현재 트랜잭션 처리 방식

**장점:**
- ✅ ACID 보장 (단일 DB 트랜잭션)
- ✅ 롤백 간단 (@Transactional)
- ✅ 강한 일관성 (Immediate Consistency)

**단점:**
- ❌ 서비스 간 강한 결합
- ❌ 확장성 제한 (Scale-out 불가)
- ❌ 장애 전파 위험 (한 서비스 장애 시 전체 영향)

### 1.3 현재 적용된 패턴

#### 1.3.1 Orchestration 패턴 (핵심 비즈니스)

**ProcessPaymentUseCase가 중앙 조율자 역할:**

```java
public PaymentResult execute(PaymentCommand command) {
    // 1. 검증 (트랜잭션 외부)
    PaymentValidationResult validated = validatePayment(command, now);

    // 2. 재고 차감 (REQUIRES_NEW + 분산락)
    productStockService.decreaseStocksWithLock(validated.items());

    // 3. 트랜잭션 처리
    try {
        return executePaymentInTransaction(command, validated, now);
    } catch (Exception e) {
        // 4. 보상 트랜잭션
        compensateStock(validated.items(), e);
        throw e;
    }
}
```

**특징:**
- 중앙 조율자(UseCase)가 순서 제어
- 명시적 보상 트랜잭션
- 동기 처리 (즉시 응답)

#### 1.3.2 Choreography 패턴 (부가 기능)

**이벤트 기반 비동기 처리:**

```java
// 이벤트 발행
eventPublisher.publishEvent(new OrderConfirmedEvent(...));

// 이벤트 리스너 (비동기)
@Async
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handleOrderConfirmed(OrderConfirmedEvent event) {
    // 랭킹 업데이트
    productRankingService.incrementSalesCount(...);

    // 데이터 플랫폼 전송
    sendToDataPlatform(event);
}
```

**특징:**
- 느슨한 결합 (Loose Coupling)
- 비동기 처리 (성능 최적화)
- 주문 응답 속도에 영향 없음

---

## 2. MSA 전환 시나리오

### 2.1 도메인별 서비스 분리

```
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│  Order Service   │  │ Product Service  │  │  Point Service   │
│  ┌────────────┐  │  │  ┌────────────┐  │  │  ┌────────────┐  │
│  │ Order API  │  │  │  │Product API │  │  │  │ Point API  │  │
│  └────────────┘  │  │  └────────────┘  │  │  └────────────┘  │
│  ┌────────────┐  │  │  ┌────────────┐  │  │  ┌────────────┐  │
│  │ Orders DB  │  │  │  │Products DB │  │  │  │ Users DB   │  │
│  └────────────┘  │  │  └────────────┘  │  │  └────────────┘  │
└──────────────────┘  └──────────────────┘  └──────────────────┘

┌──────────────────┐  ┌──────────────────┐
│  Coupon Service  │  │  Event Service   │
│  ┌────────────┐  │  │  ┌────────────┐  │
│  │ Coupon API │  │  │  │  Kafka     │  │
│  └────────────┘  │  │  │  (MQ)      │  │
│  ┌────────────┐  │  │  └────────────┘  │
│  │ Coupons DB │  │  │                  │
│  └────────────┘  │  │                  │
└──────────────────┘  └──────────────────┘
```

### 2.2 서비스 간 통신 방식

| 통신 유형 | 방식 | 사용 사례 |
|---------|------|----------|
| 동기 (Sync) | REST API / gRPC | 재고 조회, 포인트 차감 |
| 비동기 (Async) | Kafka / RabbitMQ | 랭킹 업데이트, 알림 발송 |

---

## 3. 분산 트랜잭션 문제점

### 3.1 문제 상황

**시나리오: 주문 결제 처리**

```
Order Service    Product Service   Point Service   Coupon Service
     │                 │                 │                │
     │──재고 차감 요청──→│                 │                │
     │                 │←────성공────────│                │
     │                 │                 │                │
     │─────────────포인트 차감 요청───────→│                │
     │                 │                 │←───성공────────│
     │                 │                 │                │
     │──────────────────────쿠폰 사용 요청───────────────→│
     │                 │                 │                │←──실패!
     ❌ 롤백 불가능! (이미 다른 서비스 DB에 커밋됨)
```

### 3.2 핵심 문제

#### 3.2.1 Two-Phase Commit (2PC) 사용 불가

**2PC 문제점:**
- ❌ 높은 지연 시간 (Latency)
- ❌ 코디네이터 단일 장애점 (SPOF)
- ❌ 리소스 블로킹 (Lock 대기)
- ❌ 확장성 제한

#### 3.2.2 ACID 보장 불가

**MSA 환경:**
- ❌ Atomicity: 부분 성공/실패 발생 가능
- ❌ Consistency: 일시적 불일치 상태 존재
- ⚠️ Isolation: 서비스 간 격리 수준 다름
- ✅ Durability: 각 서비스 DB에서 보장

**결과:**
- **Eventually Consistent** (최종 일관성)만 보장 가능
- 중간 상태에서 데이터 불일치 발생

---

## 4. 대응 방안: SAGA 패턴

### 4.1 SAGA 패턴 개요

**정의:**
- 분산 트랜잭션을 여러 개의 **로컬 트랜잭션**으로 분할
- 각 단계마다 **보상 트랜잭션(Compensation)** 정의
- 최종 일관성(Eventually Consistent) 보장

### 4.2 Orchestration-based SAGA

#### 4.2.1 설계

```
                    ┌─────────────────────────┐
                    │  Payment Orchestrator   │
                    │  (Order Service)        │
                    └───────────┬─────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│   Product    │      │    Point     │      │   Coupon     │
│   Service    │      │   Service    │      │   Service    │
└──────────────┘      └──────────────┘      └──────────────┘
   ↓        ↑            ↓        ↑            ↓        ↑
 차감    복원          차감    복원          사용    복원
```

#### 4.2.2 구현 방안

**PaymentSagaOrchestrator:**

```java
@Service
@RequiredArgsConstructor
public class PaymentSagaOrchestrator {
    private final ProductServiceClient productClient;
    private final PointServiceClient pointClient;
    private final CouponServiceClient couponClient;
    private final SagaStateRepository sagaStateRepository;

    public PaymentResult executePaymentSaga(PaymentCommand command) {
        // SAGA 상태 생성
        SagaState saga = sagaStateRepository.save(
            SagaState.create(command.orderId(), SagaType.PAYMENT)
        );

        try {
            // Step 1: 재고 차감
            saga.addStep(SagaStep.DECREASE_STOCK, SagaStepStatus.PENDING);
            productClient.decreaseStock(command.items());
            saga.completeStep(SagaStep.DECREASE_STOCK);

            // Step 2: 포인트 차감
            saga.addStep(SagaStep.DEDUCT_POINTS, SagaStepStatus.PENDING);
            pointClient.deductPoints(command.userId(), command.amount());
            saga.completeStep(SagaStep.DEDUCT_POINTS);

            // Step 3: 쿠폰 사용
            saga.addStep(SagaStep.USE_COUPON, SagaStepStatus.PENDING);
            couponClient.useCoupon(command.couponId());
            saga.completeStep(SagaStep.USE_COUPON);

            // Step 4: 주문 확정
            saga.addStep(SagaStep.CONFIRM_ORDER, SagaStepStatus.PENDING);
            confirmOrder(command);
            saga.completeStep(SagaStep.CONFIRM_ORDER);

            saga.markAsCompleted();
            return PaymentResult.success();

        } catch (Exception e) {
            // 보상 트랜잭션 실행
            compensate(saga, e);
            saga.markAsFailed(e.getMessage());
            throw new SagaCompensationException("결제 실패", e);
        } finally {
            sagaStateRepository.save(saga);
        }
    }

    private void compensate(SagaState saga, Exception cause) {
        log.error("SAGA 보상 트랜잭션 시작 - sagaId: {}, cause: {}",
                  saga.getId(), cause.getMessage());

        // 역순으로 보상 실행
        List<SagaStep> completedSteps = saga.getCompletedSteps();
        Collections.reverse(completedSteps);

        for (SagaStep step : completedSteps) {
            try {
                switch (step.getType()) {
                    case DECREASE_STOCK ->
                        productClient.increaseStock(step.getData());
                    case DEDUCT_POINTS ->
                        pointClient.refundPoints(step.getData());
                    case USE_COUPON ->
                        couponClient.restoreCoupon(step.getData());
                    case CONFIRM_ORDER ->
                        cancelOrder(step.getData());
                }
                step.markAsCompensated();
            } catch (Exception e) {
                log.error("보상 실패 - step: {}", step.getType(), e);
                step.markAsCompensationFailed(e.getMessage());
                // TODO: Dead Letter Queue에 전송
            }
        }
    }
}
```

**SAGA 상태 관리:**

```java
@Entity
@Table(name = "saga_state")
public class SagaState {
    @Id
    @GeneratedValue
    private Long id;

    private Long orderId;

    @Enumerated(EnumType.STRING)
    private SagaType type;

    @Enumerated(EnumType.STRING)
    private SagaStatus status; // PENDING, COMPLETED, FAILED, COMPENSATING

    @OneToMany(cascade = CascadeType.ALL)
    private List<SagaStep> steps;

    private Instant createdAt;
    private Instant completedAt;
    private String errorMessage;

    // 비즈니스 로직...
}

@Entity
@Table(name = "saga_step")
public class SagaStep {
    @Id
    @GeneratedValue
    private Long id;

    @Enumerated(EnumType.STRING)
    private SagaStepType type;

    @Enumerated(EnumType.STRING)
    private SagaStepStatus status; // PENDING, COMPLETED, FAILED, COMPENSATED

    @Column(columnDefinition = "jsonb")
    private String data; // 보상에 필요한 데이터 (JSON)

    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
}
```

#### 4.2.3 장단점

**장점:**
- ✅ 중앙 제어로 흐름 파악 용이
- ✅ 복잡한 비즈니스 로직 처리 가능
- ✅ 디버깅 및 모니터링 용이
- ✅ 타임아웃 관리 가능

**단점:**
- ❌ Orchestrator가 단일 장애점(SPOF)
- ❌ 서비스 간 결합도 높음
- ❌ Orchestrator 부하 증가

### 4.3 Choreography-based SAGA

#### 4.3.1 설계

```
┌──────────────┐     OrderCreated     ┌──────────────┐
│    Order     │─────────────────────→│   Product    │
│   Service    │                      │   Service    │
└──────────────┘                      └──────┬───────┘
       ↑                                     │
       │                          StockDecreased
       │                                     │
       │                                     ▼
       │                              ┌──────────────┐
       │        PointsDeducted        │    Point     │
       │←─────────────────────────────│   Service    │
       │                              └──────┬───────┘
       │                                     │
       │                          PointsDeducted
       │                                     │
       │                                     ▼
       │                              ┌──────────────┐
       │        CouponUsed            │   Coupon     │
       └──────────────────────────────│   Service    │
                                      └──────────────┘
```

#### 4.3.2 구현 방안

**이벤트 기반 처리:**

```java
// Order Service
@Service
public class OrderService {
    public void createOrder(CreateOrderCommand command) {
        Order order = orderRepository.save(Order.create(command));

        // 이벤트 발행
        eventPublisher.publish(new OrderCreatedEvent(
            order.getId(),
            command.items(),
            command.amount()
        ));
    }

    @KafkaListener(topics = "coupon-used")
    public void onCouponUsed(CouponUsedEvent event) {
        Order order = orderRepository.findById(event.getOrderId())
            .orElseThrow();
        order.confirm();
        orderRepository.save(order);

        eventPublisher.publish(new OrderConfirmedEvent(order.getId()));
    }

    @KafkaListener(topics = "stock-decrease-failed")
    public void onStockDecreaseFailed(StockDecreaseFailedEvent event) {
        Order order = orderRepository.findById(event.getOrderId())
            .orElseThrow();
        order.cancel();
        orderRepository.save(order);
    }
}

// Product Service
@Service
public class ProductService {
    @KafkaListener(topics = "order-created")
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            decreaseStock(event.getItems());

            eventPublisher.publish(new StockDecreasedEvent(
                event.getOrderId(),
                event.getAmount()
            ));
        } catch (Exception e) {
            eventPublisher.publish(new StockDecreaseFailedEvent(
                event.getOrderId(),
                e.getMessage()
            ));
        }
    }

    @KafkaListener(topics = "order-cancelled")
    public void onOrderCancelled(OrderCancelledEvent event) {
        increaseStock(event.getItems()); // 보상
    }
}

// Point Service
@Service
public class PointService {
    @KafkaListener(topics = "stock-decreased")
    public void onStockDecreased(StockDecreasedEvent event) {
        try {
            deductPoints(event.getUserId(), event.getAmount());

            eventPublisher.publish(new PointsDeductedEvent(
                event.getOrderId(),
                event.getUserId(),
                event.getAmount()
            ));
        } catch (Exception e) {
            // 보상: 재고 복원 이벤트 발행
            eventPublisher.publish(new PointDeductionFailedEvent(
                event.getOrderId(),
                event.getItems()
            ));
        }
    }

    @KafkaListener(topics = "order-cancelled")
    public void onOrderCancelled(OrderCancelledEvent event) {
        refundPoints(event.getUserId(), event.getAmount()); // 보상
    }
}
```

#### 4.3.3 장단점

**장점:**
- ✅ 서비스 간 느슨한 결합
- ✅ 확장성 우수
- ✅ 단일 장애점 없음
- ✅ 새 서비스 추가 용이

**단점:**
- ❌ 흐름 파악 어려움
- ❌ 디버깅 복잡
- ❌ 순환 의존 위험
- ❌ 트랜잭션 상태 추적 어려움

---

## 5. Outbox 패턴 적용

### 5.1 문제 상황

**이벤트 발행 실패 시나리오:**

```java
@Transactional
public void createOrder(CreateOrderCommand command) {
    // 1. DB 저장 (트랜잭션 내부)
    Order order = orderRepository.save(Order.create(command));

    // 2. 이벤트 발행 (트랜잭션 외부)
    eventPublisher.publish(new OrderCreatedEvent(order.getId()));
    // ❌ Kafka 장애 시: DB는 저장되었지만 이벤트는 발행 안됨!
}
```

### 5.2 Outbox 패턴 해결

#### 5.2.1 Transactional Outbox

```java
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    @GeneratedValue
    private Long id;

    private String aggregateType; // "Order", "Product"
    private Long aggregateId;

    private String eventType; // "OrderCreated", "StockDecreased"

    @Column(columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status; // PENDING, PUBLISHED, FAILED

    private Integer retryCount;
    private Instant createdAt;
    private Instant publishedAt;
}
```

```java
@Service
public class OrderService {
    @Transactional
    public void createOrder(CreateOrderCommand command) {
        // 1. DB 저장
        Order order = orderRepository.save(Order.create(command));

        // 2. Outbox에 이벤트 저장 (같은 트랜잭션)
        OutboxEvent outboxEvent = OutboxEvent.create(
            "Order",
            order.getId(),
            "OrderCreated",
            toJson(new OrderCreatedEvent(order.getId(), ...))
        );
        outboxRepository.save(outboxEvent);

        // 트랜잭션 커밋: Order + OutboxEvent가 원자적으로 저장됨
    }
}
```

#### 5.2.2 Outbox Publisher (Polling)

```java
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000) // 1초마다
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository
            .findByStatusAndRetryCountLessThan(
                OutboxStatus.PENDING,
                MAX_RETRY_COUNT
            );

        for (OutboxEvent event : pendingEvents) {
            try {
                // Kafka로 발행
                kafkaTemplate.send(
                    event.getEventType(),
                    event.getAggregateId().toString(),
                    event.getPayload()
                ).get(); // 동기 대기

                // 성공 처리
                event.markAsPublished();
                outboxRepository.save(event);

            } catch (Exception e) {
                log.error("이벤트 발행 실패 - eventId: {}", event.getId(), e);
                event.incrementRetry();
                outboxRepository.save(event);
            }
        }
    }
}
```

#### 5.2.3 Change Data Capture (CDC) 방식

**Debezium + Kafka Connect:**

```yaml
# Debezium Connector 설정
connectors:
  - name: order-service-outbox-connector
    config:
      connector.class: io.debezium.connector.postgresql.PostgresConnector
      database.hostname: order-db
      database.port: 5432
      database.user: debezium
      database.dbname: order_service
      table.include.list: public.outbox_events
      transforms: outbox
      transforms.outbox.type: io.debezium.transforms.outbox.EventRouter
```

**장점:**
- ✅ 애플리케이션 코드 변경 최소화
- ✅ 낮은 지연 시간 (실시간 CDC)
- ✅ At-least-once 보장

**단점:**
- ❌ 인프라 복잡도 증가
- ❌ DB 권한 필요 (Replication Slot)

---

## 6. 실패 복구 전략

### 6.1 계층별 복구 전략

```
┌─────────────────────────────────────────────────────────┐
│  Layer 1: Immediate Retry (@Retryable)                 │
│  - 일시적 장애 대응 (네트워크 타임아웃 등)                  │
│  - 최대 3회, 지수 백오프                                   │
├─────────────────────────────────────────────────────────┤
│  Layer 2: Scheduled Retry (Outbox Polling)             │
│  - Outbox 테이블 기반 재시도                              │
│  - 10분마다, 최대 10회                                    │
├─────────────────────────────────────────────────────────┤
│  Layer 3: Dead Letter Queue (DLQ)                      │
│  - 최종 실패 이벤트 저장                                   │
│  - 수동 재처리 또는 알림                                   │
├─────────────────────────────────────────────────────────┤
│  Layer 4: Compensation Transaction                     │
│  - 보상 트랜잭션 실행                                      │
│  - 데이터 정합성 복구                                      │
└─────────────────────────────────────────────────────────┘
```

### 6.2 구현 예시

#### 6.2.1 Retry with Circuit Breaker

```java
@Service
public class PointServiceClient {
    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;

    @Retry(
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2),
        include = {TimeoutException.class, HttpServerErrorException.class}
    )
    @CircuitBreaker(name = "pointService", fallbackMethod = "deductPointsFallback")
    public void deductPoints(Long userId, int amount) {
        restTemplate.postForObject(
            "http://point-service/api/points/deduct",
            new DeductPointsRequest(userId, amount),
            Void.class
        );
    }

    private void deductPointsFallback(Long userId, int amount, Exception e) {
        log.error("포인트 차감 실패, DLQ로 전송 - userId: {}, amount: {}",
                  userId, amount, e);

        // Dead Letter Queue에 전송
        dlqPublisher.send(new DeductPointsFailedEvent(userId, amount, e.getMessage()));

        throw new ServiceUnavailableException("포인트 서비스 일시 장애");
    }
}
```

#### 6.2.2 Idempotency (멱등성) 보장

```java
@Service
public class PaymentIdempotencyService {
    private final RedisTemplate<String, String> redisTemplate;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    public boolean isAlreadyProcessed(String idempotencyKey) {
        String key = "idempotency:" + idempotencyKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void markAsProcessed(String idempotencyKey, String result) {
        String key = "idempotency:" + idempotencyKey;
        redisTemplate.opsForValue().set(key, result, IDEMPOTENCY_TTL);
    }
}

@RestController
public class PaymentController {
    @PostMapping("/payments")
    public ResponseEntity<PaymentResult> processPayment(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody PaymentRequest request
    ) {
        // 중복 요청 체크
        if (idempotencyService.isAlreadyProcessed(idempotencyKey)) {
            return ResponseEntity.ok(cachedResult);
        }

        PaymentResult result = paymentService.process(request);

        // 처리 완료 기록
        idempotencyService.markAsProcessed(idempotencyKey, serialize(result));

        return ResponseEntity.ok(result);
    }
}
```

---

## 7. 성능 및 일관성 트레이드오프

### 7.1 CAP 정리

**MSA 환경에서의 선택:**

```
        C (Consistency)
           /        \
          /          \
         /            \
        /              \
   CP 시스템          CA 시스템
  (강한 일관성)      (단일 DB)
      /                  \
     /                    \
    /                      \
   /                        \
  P (Partition Tolerance)  A (Availability)
                    \      /
                     \    /
                      \  /
                    AP 시스템
                  (최종 일관성)
```

**선택 기준:**

| 시나리오 | 선택 | 이유 |
|---------|------|------|
| 결제 처리 | CP | 돈 관련 정합성 중요 |
| 랭킹 업데이트 | AP | 약간의 지연 허용 가능 |
| 재고 조회 | AP | 캐시 사용, 주기적 동기화 |

### 7.2 성능 최적화 전략

#### 7.2.1 읽기 최적화 (CQRS)

```
                 ┌──────────────┐
   Write ──────→ │ Command DB   │
   (강한 일관성)  │ (Master)     │
                 └───────┬──────┘
                         │
                         │ CDC / Event
                         │
                         ▼
                 ┌──────────────┐
   Read ←────── │  Query DB    │
   (최종 일관성)  │  (Read Model)│
                 │  - Redis     │
                 │  - Elasticsearch│
                 └──────────────┘
```

```java
// Command Side (Write)
@Service
public class OrderCommandService {
    @Transactional
    public OrderId createOrder(CreateOrderCommand command) {
        Order order = Order.create(command);
        orderRepository.save(order);

        // 이벤트 발행
        eventPublisher.publish(new OrderCreatedEvent(order));

        return order.getId();
    }
}

// Query Side (Read)
@Service
public class OrderQueryService {
    private final OrderReadRepository orderReadRepository; // MongoDB

    public OrderView getOrder(Long orderId) {
        return orderReadRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @KafkaListener(topics = "order-created")
    public void onOrderCreated(OrderCreatedEvent event) {
        // Read Model 업데이트
        OrderView view = OrderView.from(event);
        orderReadRepository.save(view);
    }
}
```

#### 7.2.2 캐싱 전략

```java
@Service
public class ProductQueryService {
    @Cacheable(value = "products", key = "#productId")
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow();
    }

    @CacheEvict(value = "products", key = "#productId")
    @KafkaListener(topics = "product-updated")
    public void onProductUpdated(ProductUpdatedEvent event) {
        // 캐시 무효화
    }
}
```

### 7.3 일관성 레벨별 전략

| 레벨 | 설명 | 구현 | 사용 사례 |
|-----|------|------|----------|
| **Strong Consistency** | 즉시 일관성 | 동기 API 호출, 2PC | 결제, 재고 차감 |
| **Eventual Consistency** | 최종 일관성 | 비동기 이벤트, SAGA | 랭킹, 통계 |
| **Causal Consistency** | 인과 관계 일관성 | Vector Clock | 댓글 순서 |

---

## 8. 결론 및 권장사항

### 8.1 단계별 전환 전략

#### Phase 1: 모놀리식 최적화 (현재)
- ✅ 트랜잭션 범위 최소화
- ✅ 이벤트 기반 비동기 처리
- ✅ Outbox 패턴 적용
- **목표**: MSA 전환 준비

#### Phase 2: 하이브리드 아키텍처
- ✅ 핵심 도메인 분리 (Order, Payment)
- ✅ Orchestration SAGA 구현
- ✅ API Gateway 도입
- **목표**: 점진적 분리

#### Phase 3: 완전 MSA
- ✅ 전체 도메인 분리
- ✅ Choreography SAGA 적용
- ✅ Service Mesh 도입
- **목표**: 독립적 확장

### 8.2 권장 기술 스택

| 계층 | 기술 | 용도 |
|-----|------|------|
| **API Gateway** | Spring Cloud Gateway | 라우팅, 인증, 부하 분산 |
| **Service Mesh** | Istio, Linkerd | 서비스 간 통신 관리 |
| **Message Queue** | Kafka | 이벤트 스트리밍 |
| **Distributed Tracing** | Jaeger, Zipkin | 분산 추적 |
| **Circuit Breaker** | Resilience4j | 장애 전파 방지 |
| **Service Discovery** | Eureka, Consul | 서비스 검색 |
| **Configuration** | Spring Cloud Config | 중앙 설정 관리 |

### 8.3 핵심 원칙

1. **데이터 소유권**: 각 서비스가 자신의 데이터만 소유
2. **이벤트 기반**: 서비스 간 직접 호출 최소화
3. **멱등성**: 재시도 안전성 보장
4. **보상 트랜잭션**: 실패 시 복구 전략
5. **관찰성**: 모니터링 및 로깅 강화
6. **점진적 전환**: Big Bang 방식 지양

---

## 참고 자료

- [Chris Richardson - Microservices Patterns](https://microservices.io/patterns/index.html)
- [Martin Fowler - SAGA Pattern](https://martinfowler.com/articles/patterns-of-distributed-systems/)
- [Netflix Tech Blog - Orchestration vs Choreography](https://netflixtechblog.com/)
- [Debezium Documentation](https://debezium.io/documentation/)
- [Spring Cloud Documentation](https://spring.io/projects/spring-cloud)
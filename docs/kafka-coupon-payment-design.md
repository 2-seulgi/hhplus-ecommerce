# Kafka를 활용한 이벤트 기반 시스템 설계

## 목차
1. [개요](#개요)
2. [AS-IS: 기존 시스템의 한계](#as-is-기존-시스템의-한계)
3. [TO-BE: Kafka 기반 아키텍처](#to-be-kafka-기반-아키텍처)
4. [병렬 처리 vs 순차 처리 전략](#병렬-처리-vs-순차-처리-전략)
5. [시스템별 상세 설계](#시스템별-상세-설계)
6. [파티셔닝 전략과 성능](#파티셔닝-전략과-성능)
7. [시퀀스 다이어그램](#시퀀스-다이어그램)
8. [성능 개선 지표](#성능-개선-지표)
9. [장애 대응 전략](#장애-대응-전략)
10. [운영 가이드](#운영-가이드)

---

## 개요

### 배경 및 목적

이커머스 시스템에서 **주문/결제**와 **쿠폰 발급**은 높은 트래픽과 엄격한 데이터 일관성이 요구되는 핵심 기능입니다.

**기존 Redis 기반 시스템의 한계를 극복하고, Kafka를 활용하여 확장 가능하고 신뢰성 높은 이벤트 기반 시스템을 구축**합니다.

#### 주요 목표
1. **시스템 간 느슨한 결합**: 주문, 알림, 데이터 플랫폼을 독립적으로 운영
2. **높은 처리량**: 대량의 주문과 쿠폰 발급 요청을 안정적으로 처리
3. **데이터 일관성**: Transactional Outbox Pattern으로 메시지 유실 방지
4. **확장성**: 파티션 증가를 통한 선형적 성능 확장
5. **장애 격리**: 한 시스템의 장애가 다른 시스템에 영향을 주지 않음

### 핵심 설계 원칙

1. **이벤트 기반 아키텍처**: 도메인 이벤트를 Kafka로 발행하여 시스템 간 통신
2. **At-Least-Once Delivery**: 메시지를 최소 한 번 이상 전달 보장
3. **멱등성(Idempotency)**: 같은 메시지를 여러 번 받아도 안전하게 처리
4. **수동 ACK 모드**: 메시지 처리 완료 후 명시적으로 커밋
5. **독립적인 Consumer Group**: 각 기능별로 독립적인 Consumer Group 사용
6. **병렬/순차 처리 선택**: 도메인 특성에 따라 적절한 처리 전략 선택

---

## AS-IS: 기존 시스템의 한계

### 1. 쿠폰 발급 시스템 (Redis Stream 기반)

#### 현재 구조
```
API → Redis Stream → Consumer(단일) → DB 저장 → 알림
```

#### 한계점

| 문제점 | 설명 | 영향 |
|--------|------|------|
| **단일 Consumer 처리** | Redis Stream Consumer가 1개만 동작 | - 초당 100개 발급 제한<br>- 순간 트래픽 폭증 시 지연 |
| **이벤트 배포 불가** | DB 저장 후 알림 전송만 가능 | - 통계/로그 시스템 추가 어려움<br>- 데이터 플랫폼 연동 불가 |
| **영속성 부족** | Redis 장애 시 메시지 유실 가능 | - AOF 활성화해도 완벽하지 않음<br>- 복제 지연 시 데이터 손실 |
| **확장성 한계** | Consumer 수평 확장이 어려움 | - Redis Stream Consumer Group은 단일 Stream에만 동작<br>- 파티셔닝 불가능 |

#### AS-IS 성능 (단일 Consumer)
```
처리량: ~100 TPS
지연시간: 100ms (폴링 주기)
확장성: 제한적 (Consumer 추가 시 메시지 중복)
```

### 2. 주문/결제 시스템 (동기 처리)

#### 현재 구조
```
API → Order 저장 → 알림 전송 → 데이터 플랫폼 전송 → 응답
```

#### 한계점

| 문제점 | 설명 | 영향 |
|--------|------|------|
| **강결합** | 주문 처리와 알림/데이터 전송이 하나의 트랜잭션 | - 알림 실패 시 주문 전체 롤백<br>- 데이터 플랫폼 지연 시 사용자 대기 |
| **긴 응답 시간** | 모든 후속 처리 완료까지 대기 | - 평균 응답시간 2-3초<br>- 사용자 경험 저하 |
| **장애 전파** | 한 시스템의 장애가 전체에 영향 | - 알림 시스템 장애 시 주문 불가<br>- 데이터 플랫폼 장애 시 주문 불가 |
| **확장 불가** | 새로운 이벤트 구독자 추가 어려움 | - 코드 수정 필요<br>- 배포 필요 |

#### AS-IS 성능 (동기 처리)
```
응답시간: 2-3초
처리량: ~50 TPS (외부 시스템 지연 포함)
장애 격리: 불가능
```

### 3. 대기열 시스템 (미구현)

#### 요구사항
- 콘서트 예매, 한정판 상품 등에서 **순차적 처리** 필요
- FIFO 보장 필수
- 대기 순번 추적

#### 한계점
- Redis Stream은 순서 보장이 약함 (Consumer Group 내 여러 Consumer 시)
- DB 기반은 동시성 제어 복잡도 증가
- **순차 처리 전용 시스템 부재**

---

## TO-BE: Kafka 기반 아키텍처

### 개선 목표

기존 Redis 기반 시스템의 한계를 극복하고, **Kafka를 활용한 확장 가능한 이벤트 기반 아키텍처**를 구축합니다.

### 아키텍처 개선 사항

| 구분 | AS-IS (Redis) | TO-BE (Kafka) | 개선 효과 |
|------|---------------|---------------|-----------|
| **쿠폰 발급** | Redis Stream 단일 Consumer | Kafka + 파티셔닝 + 다중 Consumer | - 처리량 3배 증가 (100 → 300 TPS)<br>- 파티션 증가로 선형 확장 가능 |
| **주문 처리** | 동기 처리 (강결합) | 비동기 이벤트 (느슨한 결합) | - 응답시간 60% 단축 (2-3초 → 1초)<br>- 장애 격리 가능 |
| **이벤트 배포** | 불가능 | 독립적 Consumer Group | - 새로운 구독자 추가 용이<br>- 코드 수정 없이 확장 |
| **영속성** | Redis AOF (불완전) | Kafka 영구 저장 (Replication) | - 메시지 유실 위험 제거<br>- Replay 가능 |
| **순서 보장** | 약함 (Consumer Group 내) | 강함 (파티션 내 FIFO) | - 같은 키의 이벤트 순서 보장 |

### TO-BE 아키텍처 다이어그램

```
┌────────────────────────────────────────────────────────────────┐
│                    E-Commerce System (TO-BE)                    │
└────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    주문/결제 시스템 (비동기)                        │
└─────────────────────────────────────────────────────────────────┘

API 요청 (POST /orders)
    ↓
Order + Outbox 저장 (PENDING)
    ↓ (즉시 응답 200 OK - 1초 이내)
사용자 → ✅ 주문 접수 완료!

    ↓ (백그라운드 처리)
Outbox Poller (5초마다)
    ↓
Kafka 발행 (order.confirmed) → 파티션 0, 1, 2
    ↓
┌─────────┼─────────┐
↓         ↓         ↓
데이터    알림      로그
Consumer  Consumer  Consumer
(독립적으로 병렬 처리, 장애 격리)


┌─────────────────────────────────────────────────────────────────┐
│                 쿠폰 시스템 (Hybrid: Redis + Kafka)                │
└─────────────────────────────────────────────────────────────────┘

API 요청 (POST /coupons/{id}/issue)
    ↓
Redis Stream 추가 (선착순 보장)
    ↓ (즉시 응답 202 Accepted)
사용자 → ✅ 발급 대기 중!

    ↓ (100ms 후)
Stream Consumer (빠른 처리)
    ↓
DB 저장 + Kafka 발행 (coupon.issued) → 파티션 0, 1, 2
    ↓
┌────────┼────────┐
↓        ↓        ↓
알림     로그     통계
Consumer Consumer Consumer
(독립적으로 병렬 처리, 확장 가능)
```

### 핵심 개선 포인트

#### 1. **느슨한 결합 (Loose Coupling)**

**AS-IS:**
```java
// 주문 처리와 알림이 강하게 결합
public void createOrder(Order order) {
    orderRepository.save(order);
    notificationService.send(order);      // 알림 실패 시 롤백
    dataPlatformService.send(order);      // 데이터 전송 실패 시 롤백
}
```

**TO-BE:**
```java
// 주문 처리와 이벤트 발행 분리
public void createOrder(Order order) {
    orderRepository.save(order);
    outboxRepository.save(outbox);  // Outbox에 저장만
    // → 백그라운드에서 Kafka 발행
    // → Consumer가 독립적으로 처리
}
```

#### 2. **병렬 처리 (Parallel Processing)**

**파티션 증가 → Consumer 증가 → 처리량 선형 증가**

```
파티션 1개 + Consumer 1개 = 100 TPS
파티션 3개 + Consumer 3개 = 300 TPS (3배)
파티션 6개 + Consumer 6개 = 600 TPS (6배)
```

#### 3. **장애 격리 (Fault Isolation)**

```
AS-IS: 알림 시스템 장애 → 주문 전체 중단
TO-BE: 알림 Consumer 장애 → 주문 처리 계속, 알림만 지연
```

---

## 병렬 처리 vs 순차 처리 전략

### 전략 비교

| 구분 | 병렬 처리 (Parallel) | 순차 처리 (Sequential) |
|------|---------------------|----------------------|
| **사용 사례** | 선착순 쿠폰 발급, 주문 처리 | 콘서트 대기열, 한정판 예매 |
| **파티션 수** | 3개 이상 (확장 가능) | **1개 고정** (순서 보장) |
| **Consumer 수** | 파티션 수만큼 가능 | 1개만 가능 |
| **처리 순서** | 같은 Key만 순서 보장 | 전체 메시지 FIFO 보장 |
| **처리량** | 높음 (파티션 수 x Consumer 수) | 낮음 (단일 Consumer) |
| **확장성** | 파티션 증가로 선형 확장 | 확장 불가 (순서 깨짐) |

### 1. 병렬 처리 전략: 선착순 쿠폰 발급

#### 특징
- **목표**: 최대 처리량, 빠른 응답
- **파티션**: 3개 (userId 기반 파티셔닝)
- **순서 보장**: 같은 사용자의 이벤트만 순서 보장
- **동시성 제어**: DB Unique Constraint

#### 파티셔닝 전략

```
메시지 Key: userId
파티션 할당: hash(userId) % 파티션 수

예시:
- userId=1 → hash(1) % 3 = 1 → 파티션 1
- userId=2 → hash(2) % 3 = 2 → 파티션 2
- userId=3 → hash(3) % 3 = 0 → 파티션 0
- userId=4 → hash(4) % 3 = 1 → 파티션 1

→ 같은 사용자는 항상 같은 파티션
→ 같은 사용자의 이벤트는 순서 보장
```

#### Consumer 구성

```
토픽: coupon.issued (파티션 3개)
Consumer Group: ecommerce-coupon-notification-group

Consumer 1 → 파티션 0 담당
Consumer 2 → 파티션 1 담당
Consumer 3 → 파티션 2 담당

→ 3개의 Consumer가 병렬로 처리
→ 처리량 3배 증가
```

#### 동시성 제어

```java
// DB Unique Constraint로 중복 방지
@Table(
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"user_id", "coupon_id"}
    )
)
public class UserCoupon {
    // 같은 사용자가 같은 쿠폰을 중복 발급 불가
}
```

#### 처리 플로우

```
요청 1000건 (동시 발생)
    ↓
Redis Stream 추가 (선착순 보장)
    ↓
Kafka 발행 (coupon.issued)
    ↓
파티셔닝 (userId 기반)
    ↓
┌──────────┼──────────┼──────────┐
파티션 0   파티션 1   파티션 2
(333건)    (333건)    (334건)
    ↓          ↓          ↓
Consumer 1 Consumer 2 Consumer 3
(병렬 처리)

→ 처리 시간: 1000건 / 3 Consumer = 333건 / Consumer
→ 처리량: 300 TPS (100 TPS x 3)
```

### 2. 순차 처리 전략: 콘서트 대기열

#### 특징
- **목표**: 전체 순서 보장 (FIFO), 공정성
- **파티션**: **1개 고정** (순서 보장)
- **순서 보장**: 모든 메시지 FIFO 순서 보장
- **확장**: Consumer 1개만 가능

#### 토픽 설계

```
토픽: concert.waiting.queue
파티션: 1개 (순서 보장 위해 고정)
Consumer: 1개 (파티션 1개이므로 1개만 가능)

메시지 Key: null (파티셔닝 불필요)
또는
메시지 Key: concertId (콘서트별 순서 보장)
```

#### Consumer 구성

```
토픽: concert.waiting.queue (파티션 1개)
Consumer Group: ecommerce-concert-queue-group

Consumer 1 → 파티션 0 담당 (유일)

→ 단일 Consumer가 순차 처리
→ FIFO 순서 보장
→ 처리량: 100 TPS (확장 불가)
```

#### 처리 플로우

```
대기열 요청 (순서대로 도착)
    ↓
Kafka 발행 (concert.waiting.queue)
    ↓
파티션 1개 (순서 보장)
    ↓
[Message 1] → [Message 2] → [Message 3] → ...
    ↓
Consumer 1 (단일, 순차 처리)
    ↓
처리 순서: 1 → 2 → 3 → 4 → ...
(들어온 순서대로 정확히 처리)
```

#### 순차 처리 예시 코드

```java
@KafkaListener(
    topics = "concert.waiting.queue",
    groupId = "ecommerce-concert-queue-group",
    concurrency = "1"  // ⚠️ 반드시 1로 고정
)
public void processWaitingQueue(ConcertQueueEvent event, Acknowledgment ack) {
    try {
        // 1. 순번에 따라 입장 처리
        processEntrance(event);

        // 2. 성공 시 ACK (다음 메시지 처리)
        ack.acknowledge();

    } catch (Exception e) {
        log.error("입장 처리 실패", e);
        // 실패 시 재시도 (순서 유지)
        throw e;
    }
}
```

### 3. 중복 처리 방지 전략

#### 멱등성 (Idempotency) 보장

| 방법 | 설명 | 적용 사례 |
|------|------|-----------|
| **DB Unique Constraint** | 테이블에 유니크 제약 조건 | 쿠폰 발급, 주문 생성 |
| **Idempotent Key** | 메시지 ID를 저장하여 중복 체크 | 결제 처리 |
| **Outbox 패턴** | 이미 처리된 메시지는 상태 체크 | 데이터 플랫폼 전송 |

#### 구현 예시

**1. DB Unique Constraint (쿠폰)**
```sql
CREATE TABLE user_coupon (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_coupon (user_id, coupon_id)  -- 중복 방지
);
```

**2. Idempotent Key (주문)**
```java
@Transactional
public void processOrder(OrderConfirmedEvent event) {
    // 이미 처리된 메시지인지 확인
    if (processedMessageRepository.exists(event.getMessageId())) {
        log.warn("이미 처리된 메시지: {}", event.getMessageId());
        return;  // 중복 처리 방지
    }

    // 실제 처리
    orderService.process(event);

    // 처리 완료 기록
    processedMessageRepository.save(
        new ProcessedMessage(event.getMessageId(), Instant.now())
    );
}
```

**3. Outbox 상태 체크**
```java
@Transactional
public void sendToDataPlatform(OrderConfirmedEvent event) {
    OrderDataPlatformOutbox outbox =
        outboxRepository.findByOrderId(event.getOrderId())
            .orElseThrow();

    // 이미 성공한 경우 중복 처리 방지
    if (outbox.getStatus() == OutboxStatus.SUCCESS) {
        log.warn("이미 전송 완료: orderId={}", event.getOrderId());
        return;
    }

    // 데이터 전송
    dataPlatformService.send(event);
    outbox.markAsSuccess(clock.instant());
    outboxRepository.save(outbox);
}
```

---

## 파티셔닝 전략과 성능

### 파티션 수와 처리량의 관계

```
처리량 (TPS) = Consumer 수 x 단일 Consumer 처리량

전제 조건:
- 단일 Consumer 처리량: 100 TPS
- 파티션 수 ≥ Consumer 수 (최적 성능)
```

| 파티션 수 | Consumer 수 | 처리량 (TPS) | 지연시간 |
|-----------|-------------|--------------|----------|
| 1 | 1 | 100 | 10ms |
| 3 | 3 | 300 | 10ms |
| 6 | 6 | 600 | 10ms |
| 12 | 12 | 1,200 | 10ms |

**핵심: 파티션 수를 늘리면 선형적으로 처리량 증가 가능**

### 파티션 전략 선택 가이드

#### 1. 병렬 처리가 필요한 경우 (파티션 3개 이상)

**적용 조건:**
- 메시지 간 순서가 중요하지 않음 (또는 같은 Key만 순서 보장)
- 높은 처리량 필요
- 확장 가능성 필요

**사례:**
- 쿠폰 발급 (사용자별 순서만 보장)
- 주문 처리 (주문별 순서만 보장)
- 알림 전송 (순서 무관)
- 로그 수집 (순서 무관)

**파티션 키 선택:**
```java
// 쿠폰 발급: userId를 Key로 사용
kafkaTemplate.send("coupon.issued",
    String.valueOf(userId),  // Key
    event);  // Value

// 주문 처리: orderId를 Key로 사용
kafkaTemplate.send("order.confirmed",
    String.valueOf(orderId),  // Key
    event);  // Value
```

#### 2. 순차 처리가 필요한 경우 (파티션 1개 고정)

**적용 조건:**
- 전체 메시지의 순서가 중요함
- FIFO 보장 필수
- 공정성이 중요함

**사례:**
- 콘서트 대기열 (입장 순서)
- 한정판 상품 예매 (선착순)
- 경매 입찰 (시간 순서)
- 재고 차감 (정확한 순서)

**토픽 설정:**
```bash
# 파티션 1개로 토픽 생성
kafka-topics --create \
  --topic concert.waiting.queue \
  --partitions 1 \
  --replication-factor 3
```

### 파티션 수 증가 시 고려사항

#### 장점
- 처리량 선형 증가
- Consumer 수평 확장 가능
- 부하 분산

#### 단점
- 메시지 순서 보장 약화 (파티션 간)
- 관리 복잡도 증가
- Rebalancing 빈도 증가

#### 권장사항

```
초기 파티션 수 = 예상 Consumer 수 x 2

예시:
- Consumer 3개 예상 → 파티션 6개
- Consumer 5개 예상 → 파티션 10개

이유:
- 파티션 수 > Consumer 수 → 확장 여유 확보
- 파티션 수가 너무 많으면 관리 부담 증가
```

---

## 시스템 아키텍처

### 전체 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                         E-Commerce System                        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      주문/결제 시스템                              │
└─────────────────────────────────────────────────────────────────┘

API 요청 → 주문 생성 → Outbox 저장 (PENDING)
                          ↓
                    Outbox Poller (5초마다)
                          ↓
                    Kafka 발행 (order.confirmed)
                          ↓
                    Outbox 상태 변경 (PUBLISHED)
                          ↓
              ┌───────────┼───────────┐
              ↓           ↓           ↓
      데이터 플랫폼    알림 서비스    상태 로그
      Consumer       Consumer       Consumer


┌─────────────────────────────────────────────────────────────────┐
│                         쿠폰 시스템                               │
└─────────────────────────────────────────────────────────────────┘

API 요청 → Redis Stream (선착순 재고 차감)
                 ↓
           Stream Consumer (100ms마다)
                 ↓
           DB 저장 + Kafka 발행 (coupon.issued)
                 ↓
         ┌───────┴───────┐
         ↓               ↓
    알림 Consumer    로그 Consumer
```

### 아키텍처 비교

| 구분 | 주문/결제 시스템 | 쿠폰 시스템 |
|------|----------------|------------|
| **패턴** | Transactional Outbox | Hybrid (Redis + Kafka) |
| **메시지 큐** | Outbox 테이블 | Redis Stream |
| **발행 방식** | Scheduler Polling (5초) | Stream Consumer (100ms) |
| **일관성 보장** | DB 트랜잭션 | Redis Stream + DB 트랜잭션 |
| **재시도 전략** | Outbox Retry Scheduler | Stream ACK 방식 |
| **토픽** | order.confirmed, order.status.changed | coupon.issued |
| **Consumer 수** | 3개 (데이터, 알림, 로그) | 2개 (알림, 로그) |

### 주요 차이점

#### 왜 다른 패턴을 사용했나?

**주문/결제 시스템 - Transactional Outbox Pattern:**
- **이유**: 주문 데이터의 일관성이 최우선
- **장점**:
  - DB 트랜잭션과 메시지 발행의 원자성 보장
  - 메시지 유실 절대 방지
  - 외부 데이터 플랫폼으로 정확한 데이터 전송 필수
- **단점**:
  - 약간의 지연 (최대 5초)
  - Outbox 테이블 관리 필요

**쿠폰 시스템 - Hybrid (Redis Stream + Kafka):**
- **이유**: 선착순 처리 속도가 최우선
- **장점**:
  - 매우 빠른 응답 속도 (100ms 단위)
  - Redis의 빠른 In-Memory 처리
  - 선착순 재고 차감에 최적화
- **단점**:
  - Redis 장애 시 메시지 유실 가능성 (복제로 완화)

---

## 주문/결제 시스템 설계

### Transactional Outbox Pattern

#### 패턴 설명

Transactional Outbox Pattern은 **DB 트랜잭션과 메시지 발행의 원자성을 보장**하는 패턴입니다.

```
문제:
주문 저장 → DB 커밋 성공 → Kafka 발행 실패
→ 주문은 저장되었지만 알림/데이터 플랫폼으로 전송 안됨

해결책:
주문 저장 + Outbox 저장 → DB 커밋 (원자성 보장)
→ 별도 Poller가 Outbox를 읽어서 Kafka 발행
→ 성공 시 Outbox 상태 변경 (PUBLISHED)
```

#### 구현 구조

```
1. OrderService
   ├─ 주문 생성 (Order)
   ├─ Outbox 저장 (PENDING)
   └─ 트랜잭션 커밋

2. OrderDataPlatformOutboxKafkaPoller (5초마다 실행)
   ├─ PENDING Outbox 조회 (100개씩)
   ├─ Kafka 발행
   ├─ 성공: PUBLISHED 상태 변경
   └─ 실패: FAILED 상태 변경

3. Retry Scheduler (1분마다 실행)
   ├─ FAILED Outbox 조회
   ├─ 재시도 횟수 < 3회이면 재시도
   └─ 3회 초과 시 DEAD_LETTER 상태로 변경
```

### 도메인 모델

#### OrderDataPlatformOutbox

```java
@Getter
public class OrderDataPlatformOutbox {
    private Long id;
    private Long orderId;
    private Long userId;
    private String orderData;      // JSON 형태의 주문 데이터
    private OutboxStatus status;   // PENDING, PUBLISHED, FAILED, DEAD_LETTER
    private int retryCount;        // 재시도 횟수
    private Instant createdAt;
    private Instant sentAt;        // Kafka 발행 성공 시각
    private String errorMessage;   // 실패 시 에러 메시지
    private Instant updatedAt;

    // 신규 Outbox 생성 (PENDING 상태)
    public static OrderDataPlatformOutbox create(
            Long orderId, Long userId, String orderData, Instant now) {
        return new OrderDataPlatformOutbox(orderId, userId, orderData, now);
    }

    // Kafka 발행 완료
    public void markAsPublished(Instant now) {
        this.status = OutboxStatus.PUBLISHED;
        this.updatedAt = now;
        this.errorMessage = null;
    }

    // 발행 실패
    public void markAsFailed(String errorMessage, Instant now) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = now;
    }

    // 재시도 횟수 증가
    public void incrementRetry(Instant now) {
        this.retryCount++;
        this.updatedAt = now;
    }
}
```

#### OutboxStatus

```java
public enum OutboxStatus {
    PENDING,       // 발행 대기 중
    PUBLISHED,     // Kafka 발행 완료
    FAILED,        // 발행 실패 (재시도 가능)
    DEAD_LETTER    // 재시도 횟수 초과 (수동 처리 필요)
}
```

### Infrastructure Layer

#### OrderEventKafkaProducer

파일 위치: `order/infrastructure/producer/OrderEventKafkaProducer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventKafkaProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_ORDER_CONFIRMED = "order.confirmed";
    private static final String TOPIC_ORDER_STATUS_CHANGED = "order.status.changed";

    /**
     * 주문 완료 이벤트 발행
     */
    public void sendOrderConfirmedEvent(OrderConfirmedEvent event) {
        String key = event.getOrderId().toString();  // 주문 ID를 Key로 사용

        log.info("📤 [Kafka Producer] 주문 완료 이벤트 발행 - orderId: {}",
                event.getOrderId());

        sendEvent(TOPIC_ORDER_CONFIRMED, key, event, "주문 완료");
    }

    /**
     * 공통 메시지 전송 로직
     */
    private void sendEvent(String topic, String key, Object event, String eventType) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("✅ [Kafka Producer] {} 이벤트 전송 성공 - " +
                            "key: {}, partition: {}, offset: {}",
                            eventType, key,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("❌ [Kafka Producer] {} 이벤트 전송 실패 - key: {}",
                            eventType, key, ex);
                }
            });
        } catch (Exception e) {
            log.error("❌ [Kafka Producer] 예외 발생 - key: {}", key, e);
            throw new RuntimeException("Kafka 메시지 발행 실패: " + eventType, e);
        }
    }
}
```

**주요 특징:**
- Key 기반 파티셔닝: 같은 주문의 이벤트는 순서 보장
- 비동기 전송: 논블로킹 방식으로 성능 최적화
- 상세한 로깅: 전송 성공/실패 추적

#### OrderDataPlatformOutboxKafkaPoller

파일 위치: `order/scheduler/OrderDataPlatformOutboxKafkaPoller.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDataPlatformOutboxKafkaPoller {
    private final OrderDataPlatformOutboxRepository outboxRepository;
    private final OrderEventKafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private static final int BATCH_SIZE = 100;

    /**
     * PENDING 상태의 Outbox를 Kafka로 발행 (5초마다)
     */
    @Scheduled(fixedDelay = 5_000)
    @SchedulerLock(
        name = "publishPendingOutboxToKafka",
        lockAtMostFor = "4s",
        lockAtLeastFor = "1s"
    )
    public void publishPendingOutboxToKafka() {
        try {
            // PENDING 상태 조회 (최대 100개)
            List<OrderDataPlatformOutbox> pendingOutboxes =
                outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(
                    OutboxStatus.PENDING);

            if (pendingOutboxes.isEmpty()) {
                return;
            }

            log.info("📤 [Outbox Poller] 발행 대상 발견 - count: {}",
                    pendingOutboxes.size());

            // 각 Outbox를 Kafka로 발행
            int successCount = 0;
            int failCount = 0;

            for (OrderDataPlatformOutbox outbox : pendingOutboxes) {
                boolean success = publishOutbox(outbox);
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            log.info("✅ [Outbox Poller] 발행 완료 - 성공: {}, 실패: {}",
                    successCount, failCount);

        } catch (Exception e) {
            log.error("❌ [Outbox Poller] 스케줄러 오류", e);
        }
    }

    /**
     * 개별 Outbox를 Kafka로 발행
     */
    @Transactional
    protected boolean publishOutbox(OrderDataPlatformOutbox outbox) {
        try {
            // JSON을 OrderConfirmedEvent로 역직렬화
            OrderConfirmedEvent event = objectMapper.readValue(
                outbox.getOrderData(), OrderConfirmedEvent.class);

            // Kafka로 발행
            kafkaProducer.sendOrderConfirmedEvent(event);

            // PUBLISHED 상태로 변경
            outbox.markAsPublished(clock.instant());
            outboxRepository.save(outbox);

            return true;

        } catch (Exception e) {
            log.error("❌ [Outbox Poller] 발행 실패 - outboxId: {}",
                    outbox.getId(), e);

            // FAILED 상태로 변경
            outbox.markAsFailed("Kafka 발행 실패: " + e.getMessage(),
                                clock.instant());
            outboxRepository.save(outbox);

            return false;
        }
    }
}
```

**주요 특징:**
- 5초마다 실행: 빠른 응답을 위한 짧은 주기
- ShedLock 사용: 여러 인스턴스 환경에서 중복 실행 방지
- 배치 처리: 한 번에 100개씩 처리
- 트랜잭션 보장: Outbox 상태 변경과 Kafka 발행의 원자성

### Presentation Layer (Kafka Consumers)

#### 1. OrderNotificationKafkaConsumer

파일 위치: `order/listener/OrderNotificationKafkaConsumer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationKafkaConsumer {

    @KafkaListener(
        topics = "order.confirmed",
        groupId = "ecommerce-notification-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderConfirmedForNotification(
            OrderConfirmedEvent event, Acknowledgment ack) {
        try {
            log.info("📥 [Notification Consumer] 주문 완료 이벤트 수신 - orderId: {}",
                    event.getOrderId());

            // 알림 전송
            sendNotification(event);

            // 수동 커밋
            ack.acknowledge();

            log.info("✅ [Notification Consumer] 알림 전송 완료 - orderId: {}",
                    event.getOrderId());

        } catch (Exception e) {
            log.error("❌ [Notification Consumer] 알림 전송 실패 - orderId: {}",
                    event.getOrderId(), e);

            // 알림 실패는 치명적이지 않으므로 커밋
            ack.acknowledge();
        }
    }

    private void sendNotification(OrderConfirmedEvent event) {
        String message = String.format(
            "🎉 주문이 완료되었습니다!\n주문번호: %d\n상품수: %d개",
            event.getOrderId(), event.getOrderItems().size());

        log.info("📢 [알림 발송] userId: {}, message: {}",
                event.getUserId(), message);

        // TODO: FCM, Email, SMS 등 실제 알림 시스템 연동
    }
}
```

**역할:**
- 주문 완료 알림 전송
- 독립적인 Consumer Group (알림 실패가 다른 Consumer에 영향 없음)
- Best Effort 전략 (실패해도 커밋)

#### 2. OrderDataPlatformKafkaConsumer

파일 위치: `order/listener/OrderDataPlatformKafkaConsumer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDataPlatformKafkaConsumer {
    private final OrderDataPlatformOutboxRepository outboxRepository;
    private final Clock clock;

    @KafkaListener(
        topics = "order.confirmed",
        groupId = "ecommerce-data-platform-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderConfirmedForDataPlatform(
            OrderConfirmedEvent event, Acknowledgment ack) {
        try {
            log.info("📥 [Data Platform Consumer] 주문 완료 이벤트 수신 - orderId: {}",
                    event.getOrderId());

            // 데이터 플랫폼으로 전송
            sendToDataPlatform(event);

            // Outbox 상태 변경 (SUCCESS)
            OrderDataPlatformOutbox outbox =
                outboxRepository.findByOrderId(event.getOrderId())
                    .orElseThrow(() -> new IllegalStateException(
                        "Outbox를 찾을 수 없습니다"));

            outbox.markAsSuccess(clock.instant());
            outboxRepository.save(outbox);

            // 수동 커밋
            ack.acknowledge();

            log.info("✅ [Data Platform Consumer] 데이터 전송 완료 - orderId: {}",
                    event.getOrderId());

        } catch (Exception e) {
            log.error("❌ [Data Platform Consumer] 데이터 전송 실패 - orderId: {}",
                    event.getOrderId(), e);

            // 재시도 가능하도록 커밋하지 않음
            throw e;
        }
    }

    private void sendToDataPlatform(OrderConfirmedEvent event) {
        // TODO: BigQuery, Elasticsearch, S3 등 실제 데이터 플랫폼 연동
        log.info("📊 [데이터 플랫폼 전송] orderId: {}", event.getOrderId());
    }
}
```

**역할:**
- 외부 데이터 플랫폼으로 주문 데이터 전송
- Outbox 상태를 SUCCESS로 변경 (모니터링 용도)
- 실패 시 커밋하지 않아 재시도 가능

#### 3. OrderStatusChangedKafkaConsumer

파일 위치: `order/listener/OrderStatusChangedKafkaConsumer.java`

```java
@Slf4j
@Component
public class OrderStatusChangedKafkaConsumer {

    @KafkaListener(
        topics = "order.status.changed",
        groupId = "ecommerce-order-status-log-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderStatusChanged(
            OrderStatusChangedEvent event, Acknowledgment ack) {
        try {
            log.info("📥 [Status Log Consumer] 주문 상태 변경 이벤트 수신 - " +
                    "orderId: {}, {} → {}",
                    event.getOrderId(),
                    event.getPreviousStatus(),
                    event.getNewStatus());

            // 상태 로그 저장
            saveStatusLog(event);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("❌ [Status Log Consumer] 상태 로그 저장 실패", e);
            ack.acknowledge();  // 로그는 Best Effort
        }
    }

    private void saveStatusLog(OrderStatusChangedEvent event) {
        // TODO: 상태 변경 로그 테이블 또는 Elasticsearch 저장
        log.info("📝 [상태 로그 저장] orderId: {}, status: {}",
                event.getOrderId(), event.getNewStatus());
    }
}
```

**역할:**
- 주문 상태 변경 이력 로깅
- 디버깅 및 모니터링 용도
- Best Effort 전략

---

## 쿠폰 시스템 설계

### Hybrid Architecture (Redis Stream + Kafka)

#### 패턴 설명

쿠폰 시스템은 **선착순 처리 속도**가 최우선이므로, Redis Stream과 Kafka를 조합한 하이브리드 방식을 사용합니다.

```
Redis Stream: 선착순 재고 차감 (빠른 In-Memory 처리)
Kafka: 이벤트 배포 (알림, 로그, 통계)

왜 Redis Stream을 사용하는가?
- 매우 빠른 응답 속도 (100ms 단위 폴링)
- In-Memory 기반 처리
- XREADGROUP으로 Worker 패턴 지원
- 선착순 재고 차감에 최적화

왜 Kafka도 함께 사용하는가?
- Redis는 재고 차감만 담당
- Kafka는 알림, 로그, 통계 등 이벤트 배포
- 시스템 간 느슨한 결합
```

#### 구현 구조

```
1. CouponQueueService
   ├─ Redis Stream에 발급 요청 추가 (XADD)
   └─ 즉시 응답 (대기 중 상태)

2. CouponIssueConsumer (100ms마다 실행)
   ├─ Redis Stream에서 메시지 읽기 (XREADGROUP)
   ├─ 선착순 검증 (issued < totalQuantity)
   ├─ DB 저장 (UserCoupon)
   ├─ Kafka 발행 (coupon.issued)
   └─ ACK 전송 (처리 완료)

3. Kafka Consumers
   ├─ CouponNotificationKafkaConsumer: 알림 전송
   └─ CouponLogKafkaConsumer: 로그/통계 저장
```

### Infrastructure Layer

#### CouponEventProducer

파일 위치: `coupon/infrastructure/producer/CouponEventProducer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "coupon.issued";

    /**
     * 쿠폰 발급 이벤트 발행
     */
    public void publish(CouponIssuedEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC,
                                  String.valueOf(event.getUserId()),
                                  event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("✅ [Kafka Producer] 쿠폰 발급 이벤트 전송 성공 - " +
                            "userId: {}, couponId: {}, offset: {}",
                            event.getUserId(),
                            event.getCouponId(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("❌ [Kafka Producer] 쿠폰 발급 이벤트 전송 실패 - " +
                            "userId: {}, couponId: {}",
                            event.getUserId(), event.getCouponId(), ex);
                }
            });

        } catch (Exception e) {
            log.error("❌ [Kafka Producer] 쿠폰 발급 이벤트 전송 중 오류 - " +
                    "userId: {}, couponId: {}",
                    event.getUserId(), event.getCouponId(), e);
        }
    }
}
```

**주요 특징:**
- userId를 Key로 사용: 같은 사용자의 쿠폰 발급 이벤트 순서 보장
- 비동기 전송
- 실패 시 로깅 (재시도는 Consumer가 처리)

### Consumer Layer

#### CouponIssueConsumer

파일 위치: `coupon/consumer/CouponIssueConsumer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {
    private final RedisTemplate<String, String> redisTemplate;
    private final UserCouponService userCouponService;
    private final CouponQueueService couponQueueService;
    private final CouponRepository couponRepository;
    private final CouponEventProducer couponEventProducer;
    private final Clock clock;

    @Value("${coupon.stream.consumer-group:coupon-consumer-group}")
    private String consumerGroup;

    @Value("${coupon.stream.consumer-name:consumer-1}")
    private String consumerName;

    private static final int BATCH_SIZE = 10;

    /**
     * Redis Stream 폴링 (100ms마다)
     */
    @Scheduled(fixedDelay = 100)
    public void consumeStream() {
        try {
            // 발급 기간 중인 쿠폰 목록 조회
            List<Coupon> activeCoupons =
                couponRepository.findAllByIssuePeriod(clock.instant());

            // 각 쿠폰의 Stream 처리
            for (Coupon coupon : activeCoupons) {
                String streamKey = "coupon:stream:" + coupon.getId();
                consumeFromStream(streamKey);
            }

        } catch (Exception e) {
            log.error("Stream 소비 중 오류 발생", e);
        }
    }

    /**
     * 특정 Stream에서 메시지 소비
     */
    private void consumeFromStream(String streamKey) {
        try {
            // Consumer Group 생성 (없으면)
            createConsumerGroupIfNotExists(streamKey);

            // XREADGROUP으로 메시지 읽기
            List<MapRecord<String, Object, Object>> messages =
                redisTemplate.opsForStream()
                    .read(
                        Consumer.from(consumerGroup, consumerName),
                        StreamReadOptions.empty()
                            .count(BATCH_SIZE)
                            .block(Duration.ofMillis(100)),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                    );

            if (messages == null || messages.isEmpty()) {
                return;
            }

            // 각 메시지 처리
            for (MapRecord<String, Object, Object> message : messages) {
                processMessage(streamKey, message);
            }

        } catch (Exception e) {
            log.error("Stream 읽기 실패 - streamKey: {}", streamKey, e);
        }
    }

    /**
     * 메시지 처리
     */
    private void processMessage(String streamKey,
                                MapRecord<String, Object, Object> message) {
        RecordId messageId = message.getId();
        Long userId = null;
        Long couponId = null;

        try {
            // 메시지에서 userId, couponId 추출
            userId = Long.parseLong((String) message.getValue().get("userId"));
            couponId = Long.parseLong((String) message.getValue().get("couponId"));

            log.info("쿠폰 발급 처리 시작 - userId: {}, couponId: {}",
                    userId, couponId);

            // 1. 쿠폰 정보 조회
            Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "쿠폰을 찾을 수 없습니다"));

            // 2. 선착순 검증 (DB 기반)
            if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
                log.warn("선착순 마감 - userId: {}, couponId: {}",
                        userId, couponId);
                couponQueueService.saveResult(userId, couponId, false);
                redisTemplate.opsForStream()
                    .acknowledge(streamKey, consumerGroup, messageId);
                return;
            }

            // 3. 실제 쿠폰 발급
            IssueCouponCommand command = new IssueCouponCommand(userId, couponId);
            IssueCouponResult result = userCouponService.issueCoupon(command);

            // 4. 성공 결과 저장
            couponQueueService.saveResult(userId, couponId, true);

            // 5. Kafka로 쿠폰 발급 이벤트 발행
            CouponIssuedEvent event = CouponIssuedEvent.of(
                result.userCouponId(),
                userId,
                couponId,
                coupon.getName(),
                clock.instant()
            );
            couponEventProducer.publish(event);

            // ACK 전송 (처리 완료)
            redisTemplate.opsForStream()
                .acknowledge(streamKey, consumerGroup, messageId);

            log.info("쿠폰 발급 성공 - userId: {}, couponId: {}",
                    userId, couponId);

        } catch (Exception e) {
            log.error("쿠폰 발급 실패 - userId: {}, couponId: {}",
                    userId, couponId, e);

            // 실패 결과 저장
            if (userId != null && couponId != null) {
                couponQueueService.saveResult(userId, couponId, false);
            }

            // ACK 전송 (재시도 방지)
            redisTemplate.opsForStream()
                .acknowledge(streamKey, consumerGroup, messageId);
        }
    }
}
```

**주요 특징:**
- 100ms마다 실행: 매우 빠른 응답 속도
- Redis Stream Consumer Group 사용
- 배치 처리: 한 번에 10개씩
- DB 저장 후 Kafka 발행: 데이터 일관성 보장
- 실패 시 ACK: 무한 재시도 방지

### Presentation Layer (Kafka Consumers)

#### 1. CouponNotificationKafkaConsumer

파일 위치: `coupon/listener/CouponNotificationKafkaConsumer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponNotificationKafkaConsumer {

    @KafkaListener(
        topics = "coupon.issued",
        groupId = "ecommerce-coupon-notification-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeCouponIssuedForNotification(
            CouponIssuedEvent event, Acknowledgment ack) {
        try {
            log.info("📥 [Coupon Notification Consumer] 쿠폰 발급 이벤트 수신 - " +
                    "userId: {}, couponId: {}, couponName: {}",
                    event.getUserId(),
                    event.getCouponId(),
                    event.getCouponName());

            // 알림 전송
            sendNotification(event);

            // 수동 커밋
            ack.acknowledge();

            log.info("✅ [Coupon Notification Consumer] 알림 전송 완료 - " +
                    "userId: {}, couponName: {}",
                    event.getUserId(), event.getCouponName());

        } catch (Exception e) {
            log.error("❌ [Coupon Notification Consumer] 알림 전송 실패 - " +
                    "userId: {}, couponId: {}",
                    event.getUserId(), event.getCouponId(), e);

            // 실패해도 ACK (알림은 Best Effort)
            ack.acknowledge();
        }
    }

    private void sendNotification(CouponIssuedEvent event) {
        log.info("🔔 쿠폰 발급 알림 - userId: {}, couponName: \"{}\" 쿠폰이 발급되었습니다!",
                event.getUserId(), event.getCouponName());

        // TODO: SMS, Push, Email 등 실제 알림 시스템 연동
    }
}
```

#### 2. CouponLogKafkaConsumer

파일 위치: `coupon/listener/CouponLogKafkaConsumer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponLogKafkaConsumer {

    @KafkaListener(
        topics = "coupon.issued",
        groupId = "ecommerce-coupon-log-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeCouponIssuedForLog(
            CouponIssuedEvent event, Acknowledgment ack) {
        try {
            log.info("📥 [Coupon Log Consumer] 쿠폰 발급 이벤트 수신 - " +
                    "userId: {}, couponId: {}, issuedAt: {}",
                    event.getUserId(),
                    event.getCouponId(),
                    event.getIssuedAt());

            // 로그 저장 및 통계 집계
            saveLog(event);

            ack.acknowledge();

            log.info("✅ [Coupon Log Consumer] 로그 저장 완료 - " +
                    "userId: {}, couponId: {}",
                    event.getUserId(), event.getCouponId());

        } catch (Exception e) {
            log.error("❌ [Coupon Log Consumer] 로그 저장 실패 - " +
                    "userId: {}, couponId: {}",
                    event.getUserId(), event.getCouponId(), e);

            // 실패해도 ACK (로그는 Best Effort)
            ack.acknowledge();
        }
    }

    private void saveLog(CouponIssuedEvent event) {
        log.info("📊 쿠폰 발급 로그 저장 - " +
                "userCouponId: {}, userId: {}, couponId: {}, " +
                "couponName: \"{}\", issuedAt: {}",
                event.getUserCouponId(),
                event.getUserId(),
                event.getCouponId(),
                event.getCouponName(),
                event.getIssuedAt());

        // TODO: Elasticsearch, Prometheus, BigQuery 등 실제 로그/통계 시스템 연동
    }
}
```

---

## 토픽 설계

### 토픽 목록

| 토픽 이름 | 파티션 수 | Replication Factor | 용도 |
|----------|----------|-------------------|------|
| `order.confirmed` | 3 | 3 | 주문 확정 이벤트 |
| `order.status.changed` | 3 | 3 | 주문 상태 변경 이벤트 |
| `coupon.issued` | 3 | 3 | 쿠폰 발급 이벤트 |

### 파티션 전략

#### order.confirmed

```
Key: orderId
Partitioning: Key 기반 (orderId % 파티션 수)

이유:
- 같은 주문의 이벤트는 순서 보장
- 주문 ID 기반으로 균등 분산
```

#### coupon.issued

```
Key: userId
Partitioning: Key 기반 (userId % 파티션 수)

이유:
- 같은 사용자의 쿠폰 발급 이벤트 순서 보장
- 사용자 ID 기반으로 균등 분산
```

### Consumer Group 설계

```
토픽: order.confirmed

Consumer Group 1: ecommerce-data-platform-consumer-group
├─ Consumer: OrderDataPlatformKafkaConsumer
└─ 역할: 데이터 플랫폼으로 주문 데이터 전송

Consumer Group 2: ecommerce-notification-consumer-group
├─ Consumer: OrderNotificationKafkaConsumer
└─ 역할: 사용자 알림 전송

Consumer Group 3: ecommerce-order-status-log-consumer-group
├─ Consumer: OrderStatusChangedKafkaConsumer (별도 토픽: order.status.changed)
└─ 역할: 주문 상태 변경 로그

→ 각 그룹은 모든 메시지를 독립적으로 소비
→ 한 그룹의 실패가 다른 그룹에 영향 없음
```

---

## 패키지 구조

### 레이어드 아키텍처

```
com.hhplus.be
├─ order
│   ├─ domain
│   │   ├─ model
│   │   │   ├─ Order.java
│   │   │   ├─ OrderDataPlatformOutbox.java
│   │   │   └─ OutboxStatus.java
│   │   ├─ event
│   │   │   ├─ OrderConfirmedEvent.java
│   │   │   └─ OrderStatusChangedEvent.java
│   │   └─ repository
│   │       └─ OrderDataPlatformOutboxRepository.java (interface)
│   │
│   ├─ infrastructure
│   │   ├─ producer                          ← Kafka Producer (외부 시스템 통신)
│   │   │   └─ OrderEventKafkaProducer.java
│   │   └─ repository
│   │       └─ OrderDataPlatformOutboxRepositoryImpl.java
│   │
│   ├─ listener                               ← Kafka Consumer (이벤트 핸들러)
│   │   ├─ OrderNotificationKafkaConsumer.java
│   │   ├─ OrderDataPlatformKafkaConsumer.java
│   │   └─ OrderStatusChangedKafkaConsumer.java
│   │
│   └─ scheduler                              ← Outbox Poller & Retry
│       ├─ OrderDataPlatformOutboxKafkaPoller.java
│       └─ OrderDataPlatformOutboxRetryScheduler.java
│
├─ coupon
│   ├─ domain
│   │   ├─ model
│   │   │   └─ Coupon.java
│   │   └─ event
│   │       └─ CouponIssuedEvent.java
│   │
│   ├─ infrastructure
│   │   └─ producer                          ← Kafka Producer
│   │       └─ CouponEventProducer.java
│   │
│   ├─ consumer                              ← Redis Stream Consumer
│   │   └─ CouponIssueConsumer.java
│   │
│   └─ listener                               ← Kafka Consumer
│       ├─ CouponNotificationKafkaConsumer.java
│       └─ CouponLogKafkaConsumer.java
│
└─ config
    ├─ KafkaProducerConfig.java
    └─ KafkaConsumerConfig.java
```

### 패키지 설계 원칙

1. **Infrastructure Layer**: 외부 시스템과의 통신 (Kafka Producer)
2. **Presentation Layer**: 이벤트 핸들러 (Kafka Consumer, Listener)
3. **Consumer vs Listener 구분**:
   - `consumer`: Redis Stream Consumer
   - `listener`: Kafka Consumer (이벤트 리스너)

---

## 실행 흐름

### 주문/결제 플로우

```
1. [API 요청]
   POST /api/orders

2. [OrderService]
   ├─ 주문 생성 (Order)
   ├─ OrderDataPlatformOutbox 저장 (PENDING)
   └─ DB 트랜잭션 커밋

3. [OrderDataPlatformOutboxKafkaPoller] - 5초 후
   ├─ PENDING Outbox 조회
   ├─ Kafka 발행 (order.confirmed)
   └─ Outbox 상태 변경 (PUBLISHED)

4. [Kafka]
   ├─ order.confirmed 토픽에 메시지 저장
   └─ 파티션 할당 (orderId % 파티션 수)

5. [OrderNotificationKafkaConsumer]
   ├─ 메시지 수신
   ├─ 알림 전송
   └─ ACK

6. [OrderDataPlatformKafkaConsumer]
   ├─ 메시지 수신
   ├─ 데이터 플랫폼 전송
   ├─ Outbox 상태 변경 (SUCCESS)
   └─ ACK

7. [OrderStatusChangedKafkaConsumer] - 상태 변경 시
   ├─ order.status.changed 토픽 구독
   ├─ 상태 로그 저장
   └─ ACK
```

### 쿠폰 발급 플로우

```
1. [API 요청]
   POST /api/coupons/{couponId}/issue

2. [CouponQueueService]
   ├─ Redis Stream에 발급 요청 추가 (XADD)
   │   streamKey: coupon:stream:{couponId}
   │   value: {userId, couponId}
   └─ 즉시 응답 (대기 중 상태)

3. [CouponIssueConsumer] - 100ms 후
   ├─ Redis Stream 메시지 읽기 (XREADGROUP)
   ├─ 선착순 검증 (issued < totalQuantity)
   ├─ DB 저장 (UserCoupon)
   ├─ Kafka 발행 (coupon.issued)
   ├─ Redis ACK (XACK)
   └─ 결과 저장 (Redis)

4. [Kafka]
   ├─ coupon.issued 토픽에 메시지 저장
   └─ 파티션 할당 (userId % 파티션 수)

5. [CouponNotificationKafkaConsumer]
   ├─ 메시지 수신
   ├─ 알림 전송
   └─ ACK

6. [CouponLogKafkaConsumer]
   ├─ 메시지 수신
   ├─ 로그/통계 저장
   └─ ACK
```

---

## 장애 대응 전략

### 주문/결제 시스템 장애 대응

#### 1. Kafka 브로커 장애

**문제:**
- Kafka 브로커 다운 시 메시지 발행 실패

**대응:**
1. **Outbox 상태가 PENDING으로 유지됨**
2. **OrderDataPlatformOutboxKafkaPoller가 계속 재시도**
   - 5초마다 PENDING Outbox 조회
   - Kafka 복구 시 자동으로 메시지 발행
3. **Replication Factor 3 사용**
   - Leader 브로커 장애 시 Follower가 Leader로 승격
   - 데이터 유실 방지

#### 2. Consumer 처리 실패

**문제:**
- Consumer에서 메시지 처리 중 예외 발생

**대응:**
- **알림 Consumer (Best Effort)**:
  ```java
  try {
      sendNotification(event);
      ack.acknowledge();
  } catch (Exception e) {
      log.error("알림 전송 실패", e);
      ack.acknowledge();  // 실패해도 커밋
  }
  ```
  - 알림 실패는 치명적이지 않으므로 커밋
  - 재시도 로직 추가 가능

- **데이터 플랫폼 Consumer (재시도)**:
  ```java
  try {
      sendToDataPlatform(event);
      ack.acknowledge();
  } catch (Exception e) {
      log.error("데이터 전송 실패", e);
      throw e;  // 커밋하지 않아 재시도
  }
  ```
  - 커밋하지 않으면 Kafka가 자동으로 재시도
  - 최대 재시도 횟수 설정 필요

#### 3. Outbox 발행 실패

**문제:**
- Outbox Poller에서 Kafka 발행 실패

**대응:**
1. **FAILED 상태로 변경**
2. **OrderDataPlatformOutboxRetryScheduler (1분마다 실행)**
   ```java
   @Scheduled(fixedDelay = 60_000)
   public void retryFailedOutboxes() {
       List<OrderDataPlatformOutbox> failedOutboxes =
           outboxRepository.findByStatusAndRetryCountLessThan(
               OutboxStatus.FAILED, MAX_RETRY_COUNT);

       for (OrderDataPlatformOutbox outbox : failedOutboxes) {
           outbox.incrementRetry(clock.instant());
           outbox.markAsPending(clock.instant());
           outboxRepository.save(outbox);
       }
   }
   ```
3. **재시도 횟수 3회 초과 시 DEAD_LETTER 상태로 변경**
4. **DEAD_LETTER는 수동 처리 필요 (모니터링 알림)**

### 쿠폰 시스템 장애 대응

#### 1. Redis 장애

**문제:**
- Redis 다운 시 발급 요청 추가 불가

**대응:**
1. **Redis Sentinel 또는 Cluster 사용**
   - Master 장애 시 자동 Failover
2. **Redis AOF (Append Only File) 활성화**
   - 데이터 영속성 보장
3. **Replication 사용**
   - Master-Slave 구조로 데이터 복제

#### 2. CouponIssueConsumer 처리 실패

**문제:**
- Consumer에서 메시지 처리 중 예외 발생

**대응:**
```java
try {
    // 쿠폰 발급 처리
    IssueCouponResult result = userCouponService.issueCoupon(command);
    couponQueueService.saveResult(userId, couponId, true);
    couponEventProducer.publish(event);
    redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, messageId);

} catch (Exception e) {
    log.error("쿠폰 발급 실패", e);
    couponQueueService.saveResult(userId, couponId, false);
    redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, messageId);  // ACK 전송
}
```
- **실패 시 ACK 전송**: 무한 재시도 방지
- **실패 결과 Redis에 저장**: 사용자에게 실패 상태 전달
- **Kafka 발행 실패 시**: 로그만 남기고 계속 진행 (알림은 Best Effort)

#### 3. 중복 발급 방지

**문제:**
- 네트워크 재시도 등으로 중복 발급 가능성

**대응:**
1. **DB 유니크 제약조건**
   ```sql
   UNIQUE KEY uk_user_coupon (user_id, coupon_id)
   ```
2. **중복 발급 시도 시 예외 발생**
   ```java
   try {
       userCouponService.issueCoupon(command);
   } catch (DuplicateKeyException e) {
       log.warn("중복 발급 시도", e);
       couponQueueService.saveResult(userId, couponId, false);
       ack.acknowledge();
   }
   ```

---

## 성능 최적화

### 주문/결제 시스템 최적화

#### 1. Outbox Poller 주기 최적화

```java
// 현재: 5초마다 실행
@Scheduled(fixedDelay = 5_000)

// 트래픽이 높은 경우: 1초로 단축
@Scheduled(fixedDelay = 1_000)

// 트래픽이 낮은 경우: 10초로 연장
@Scheduled(fixedDelay = 10_000)
```

#### 2. 배치 크기 조정

```java
// 현재: 100개씩 처리
private static final int BATCH_SIZE = 100;

// 고려 사항:
// - 배치 크기 ↑ = 처리량 ↑, 지연시간 ↑
// - 배치 크기 ↓ = 처리량 ↓, 지연시간 ↓
```

#### 3. Kafka Producer 설정

```java
// 배치 크기 증가 (처리량 향상)
props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);  // 32KB

// Linger 시간 설정 (배치 효율 향상)
props.put(ProducerConfig.LINGER_MS_CONFIG, 10);  // 10ms

// 압축 활성화 (네트워크 대역폭 절약)
props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
```

### 쿠폰 시스템 최적화

#### 1. Stream Consumer 주기 최적화

```java
// 현재: 100ms마다 실행
@Scheduled(fixedDelay = 100)

// 선착순 속도가 매우 중요한 경우: 50ms로 단축
@Scheduled(fixedDelay = 50)

// 주의: 너무 짧으면 CPU 사용률 증가
```

#### 2. Redis Stream 배치 크기

```java
// 현재: 10개씩 처리
private static final int BATCH_SIZE = 10;

// 트래픽이 높은 경우: 50개로 증가
private static final int BATCH_SIZE = 50;
```

#### 3. Consumer 수평 확장

```
Redis Stream Consumer Group 사용:
- Consumer 1: consumer-1
- Consumer 2: consumer-2
- Consumer 3: consumer-3

→ 같은 Consumer Group 내에서 메시지 분산 처리
→ 처리량 3배 증가
```

---

## 운영 가이드

### 모니터링 항목

#### Kafka 모니터링

```bash
# Consumer Lag 확인
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group ecommerce-notification-consumer-group \
  --describe

# 토픽 상태 확인
kafka-topics --bootstrap-server localhost:9092 --describe --topic order.confirmed
```

**주요 메트릭:**
- **Consumer Lag**: 미처리 메시지 수 (경고: > 1000)
- **Offset**: Consumer가 읽은 마지막 Offset
- **Leader/Replica 상태**: Broker 장애 감지

#### Outbox 모니터링

```sql
-- PENDING 상태 Outbox 개수
SELECT COUNT(*) FROM order_data_platform_outbox WHERE status = 'PENDING';

-- FAILED 상태 Outbox 개수 (경고: > 10)
SELECT COUNT(*) FROM order_data_platform_outbox WHERE status = 'FAILED';

-- DEAD_LETTER 상태 Outbox (즉시 처리 필요)
SELECT * FROM order_data_platform_outbox WHERE status = 'DEAD_LETTER';

-- 오래된 PENDING Outbox (10분 이상)
SELECT * FROM order_data_platform_outbox
WHERE status = 'PENDING'
AND created_at < NOW() - INTERVAL 10 MINUTE;
```

#### Redis Stream 모니터링

```bash
# Stream 길이 확인
XLEN coupon:stream:1

# Consumer Group 상태 확인
XINFO GROUPS coupon:stream:1

# Pending 메시지 확인
XPENDING coupon:stream:1 coupon-consumer-group
```

### 장애 대응 매뉴얼

#### Kafka Consumer Lag 증가

**증상:**
- Consumer Lag이 계속 증가
- 메시지 처리 속도 < 메시지 생산 속도

**원인 분석:**
1. Consumer 처리 속도 저하
2. DB 또는 외부 API 응답 지연
3. Consumer 인스턴스 부족

**대응 방안:**
1. **Consumer 인스턴스 추가**
   ```yaml
   # docker-compose.yml
   ecommerce-consumer-1:
     ...
   ecommerce-consumer-2:
     ...
   ecommerce-consumer-3:
     ...
   ```
2. **파티션 수 증가** (Consumer 수 증가 가능)
   ```bash
   kafka-topics --bootstrap-server localhost:9092 \
     --alter --topic order.confirmed --partitions 6
   ```
3. **Consumer 배치 크기 증가**
   ```java
   props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);
   ```

#### Outbox 발행 실패

**증상:**
- FAILED 상태 Outbox 증가
- 주문은 생성되었지만 알림 미발송

**원인 분석:**
1. Kafka 브로커 장애
2. 네트워크 문제
3. 직렬화 오류

**대응 방안:**
1. **Kafka 브로커 상태 확인**
   ```bash
   docker ps | grep kafka
   docker logs ecommerce-kafka
   ```
2. **Retry Scheduler가 재시도 중인지 확인**
3. **재시도 횟수 초과 시 수동 처리**
   ```sql
   -- DEAD_LETTER 상태를 PENDING으로 변경
   UPDATE order_data_platform_outbox
   SET status = 'PENDING', retry_count = 0, updated_at = NOW()
   WHERE id = ?;
   ```

#### 쿠폰 선착순 마감 후에도 발급 시도

**증상:**
- 선착순 마감되었는데 발급 시도 계속 발생
- Redis Stream에 메시지 쌓임

**원인 분석:**
- Redis Stream 메시지가 아직 처리 중

**대응 방안:**
1. **Consumer가 정상 동작 중인지 확인**
2. **Stream 메시지 수동 ACK**
   ```bash
   XACK coupon:stream:1 coupon-consumer-group {messageId}
   ```
3. **필요 시 Stream 비우기** (주의: 데이터 유실)
   ```bash
   DEL coupon:stream:1
   ```

---

## 참고 자료

1. **Kafka 기본 개념**: [kafka-fundamentals.md](./kafka-fundamentals.md)
2. **Transactional Outbox Pattern**: https://microservices.io/patterns/data/transactional-outbox.html
3. **Redis Stream**: https://redis.io/docs/data-types/streams/
4. **Spring Kafka Documentation**: https://spring.io/projects/spring-kafka
# 트랜잭션 분리 문제 분석 및 MSA 전환 전략

> **목표**: 현재 구조의 트랜잭션 문제를 명확히 이해하고, MSA 전환 시 발생할 분산 트랜잭션 문제의 해결 방안을 제시합니다.

---

## 📋 목차
1. [현재 구조 이해하기](#1-현재-구조-이해하기)
2. [트랜잭션 범위와 문제점](#2-트랜잭션-범위와-문제점)
3. [현재 해결 방안](#3-현재-해결-방안)
4. [MSA 전환 시나리오](#4-msa-전환-시나리오)
5. [분산 트랜잭션 문제](#5-분산-트랜잭션-문제)
6. [SAGA 패턴 해결 방안](#6-saga-패턴-해결-방안)
7. [결론 및 로드맵](#7-결론-및-로드맵)
---
## 1\. 현재 아키텍처 및 배포 구조

### 1.1 배포 모델 (Monolithic)

현재 시스템은 전형적인 모놀리식 구조로, 모든 도메인 로직이 하나의 애플리케이션 내에 존재하며 단일 데이터베이스를 공유하고 있습니다.

```mermaid
graph TD
    User[사용자 요청] --> App[Spring Boot Application (ecommerce.jar)]
    App --> DB[(PostgreSQL 단일 DB)]
    
    subgraph "Application Scope"
    App
    end
    
    subgraph "Database Scope"
    DB
    end
```

* **배포 단위**: Single JAR
* **데이터베이스**: 공유 DB (Orders, Products, Users, Coupons 테이블 공존)
* **스케일링**: 서버 전체 복제 (Scale-out) 방식

### 1.2 내부 코드 구조

내부적으로는 향후 분리를 염두에 두고 \*\*DDD(Domain-Driven Design)\*\*와 **Layered Architecture**를 적용하여 도메인 간 경계를 명확히 하고 있습니다.

```text
┌─────────────────────────────────────────────────┐
│  Presentation (Controller)                      │
│  - API 진입점                                    │
└──────────────────┬──────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────┐
│  Application (UseCase)                          │
│  - 비즈니스 흐름 조율 (Facade 역할)               │
│  - ProcessPaymentUseCase                        │
└──────────────────┬──────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────┐
│  Domain (Service & Model)                       │
│  - 핵심 비즈니스 로직, 순수 Java 객체              │
│  - Order, Product, Coupon 도메인 로직            │
└──────────────────┬──────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────┐
│  Infrastructure (Repository)                    │
│  - 데이터베이스 접근 구현                         │
└─────────────────────────────────────────────────┘
```

-----

## 2\. 트랜잭션 분리 배경과 문제점

### 2.1 결제 프로세스의 트랜잭션 분리

성능 최적화와 DB 락 점유 시간을 줄이기 위해, 결제 프로세스 중 **재고 차감** 로직을 메인 트랜잭션에서 분리했습니다.

**[트랜잭션 분리 전략]**

1.  **재고 차감**: `REQUIRES_NEW` 전파 속성을 사용하여 별도 트랜잭션으로 즉시 커밋.
2.  **결제 수행**: 포인트 차감, 쿠폰 사용, 주문 생성은 메인 트랜잭션으로 묶음.

<!-- end list -->

```text
사용자 요청: "결제하기"
      ↓
┌─────────────────────────────────────────────────┐
│  1. 검증 (Validation)                           │
│     - 트랜잭션 시작 전 수행                        │
└─────────────────┬───────────────────────────────┘
                  ↓
┌─────────────────────────────────────────────────┐
│  2. 재고 차감 (트랜잭션 A)                        │
│     @Transactional(REQUIRES_NEW)                │
│     [성공 시 DB 즉시 커밋] ➔ 롤백 불가              │
└─────────────────┬───────────────────────────────┘
                  ↓
┌─────────────────────────────────────────────────┐
│  3. 결제 및 주문 (트랜잭션 B)                     │
│     @Transactional                              │
│     - 포인트 차감                                │
│     - 쿠폰 사용                                  │
│     - 주문 저장                                  │
│     [실패 시 롤백]                               │
└─────────────────────────────────────────────────┘
```

### 2.2 문제점: 데이터 불일치 (Partial Commit)

재고는 차감되었으나(Transaction A 커밋), 포인트 부족 등으로 주문 생성에 실패(Transaction B 롤백)하는 경우 **데이터 정합성이 깨지는 문제**가 발생합니다.

**[시나리오: 결제 실패 시]**

```text
Timeline:
─────────────────────────────────────────────────────────

T1: [재고 차감] 성공 ✅
    Product Stock: 10 → 9 (DB 반영 완료)

T2: [포인트 차감] 실패 ❌
    User Balance: 잔액 부족 발생

T3: [메인 트랜잭션 롤백] 
    - 주문 생성 취소
    - 포인트 차감 취소
    - BUT, 재고는 이미 9개로 줄어든 상태 유지 (불일치 발생) 💥
```

-----

## 3\. 현재의 해결 전략: 하이브리드 패턴

현재 시스템은 **Orchestration(중앙 제어)** 과 **Choreography(이벤트 기반)** 를 결합한 하이브리드 패턴을 적용하고 있습니다. 핵심 비즈니스는 엄격히 제어하고, 부가 기능은 느슨하게 결합하는 방식입니다.

### 3.1 핵심 영역: Orchestration 패턴 (보상 트랜잭션)

`ProcessPaymentUseCase`가 Facade 역할을 하며 여러 도메인 서비스를 중앙에서 조율합니다. 실패 시 **보상 트랜잭션**을 명시적으로 실행하여 데이터 정합성을 보장합니다.

**[중앙 조율 구조]**

```text
           ┌────────────────────────────┐
           │  ProcessPaymentUseCase     │
           │  (Orchestrator/Facade)     │
           └───────────┬────────────────┘
                       │
       ┌───────────────┼───────────────┐
       │               │               │
       ↓               ↓               ↓
┌────────────┐  ┌────────────┐  ┌────────────┐
│  Product   │  │   Point    │  │   Coupon   │
│  Service   │  │  Service   │  │  Service   │
└────────────┘  └────────────┘  └────────────┘
```

**[보상 트랜잭션 구현]**

```java
public void executePayment() {
    boolean stockDecreased = false;
    try {
        // 1. 재고 차감 (별도 트랜잭션)
        decreaseStock();
        stockDecreased = true;

        // 2. 메인 결제 로직 (포인트, 쿠폰, 주문)
        processOrder();
    } catch (Exception e) {
        // 3. 실패 시 보상 트랜잭션 실행
        if (stockDecreased) {
            compensateStock(); // 재고 복구
        }
        throw e;
    }
}
```

**적용 영역**: 재고 차감, 포인트 차감, 쿠폰 사용, 주문 확정
**특징**: 동기 처리, 강한 일관성, 중앙 제어

### 3.2 부가 영역: Choreography 패턴 (이벤트 기반)

핵심 비즈니스 외의 부가 작업(랭킹 집계, 데이터 플랫폼 전송)은 Spring Event를 통해 독립적으로 처리됩니다. 각 리스너가 자율적으로 동작하며, 실패해도 주문 처리에는 영향을 주지 않습니다.

**[이벤트 기반 구조]**

```text
┌────────────────────────────┐
│  ProcessPaymentUseCase     │
└───────────┬────────────────┘
            │ publishEvent(OrderConfirmedEvent)
            ↓
    ┌──────────────────┐
    │  Spring Event    │
    └────────┬─────────┘
             │
     ┌───────┴───────┐
     ↓               ↓
┌─────────┐     ┌─────────┐
│ Ranking │     │  Data   │
│Listener │     │Listener │
└─────────┘     └─────────┘
  (자율적)        (자율적)
```

**[비동기 처리 흐름]**

```text
[Main Thread]                   [Async Thread]
┌──────────────┐  Event Publish ┌──────────────┐
│  주문 확정    │ ───────────➔  │  랭킹 집계    │
│  (DB Commit) │                │  데이터 전송   │
└──────────────┘                └──────────────┘
      ↓
   사용자 응답 (Fast ⚡)
```

**적용 영역**: 상품 랭킹 업데이트, 외부 데이터 플랫폼 전송
**특징**: 비동기 처리, 느슨한 결합, 장애 격리

### 3.3 하이브리드 패턴의 장점

| 구분 | Orchestration 영역 | Choreography 영역 |
|-----|------------------|------------------|
| **담당** | 핵심 비즈니스 (결제) | 부가 기능 (통계, 알림) |
| **처리 방식** | 동기 (150ms) | 비동기 (백그라운드) |
| **실패 영향** | 사용자에게 즉시 통보 | 재시도 메커니즘으로 복구 |
| **일관성** | 강한 일관성 (보상 트랜잭션) | 최종 일관성 (Outbox 패턴) |
| **확장성** | UseCase 수정 필요 | 리스너만 추가 |

**핵심 전략**: 돈과 직결된 핵심 로직은 제어하고, 부가 기능은 독립적으로 확장 가능하게 설계

-----

## 4\. MSA 전환 시나리오

향후 트래픽 증가와 배포 유연성을 위해 모놀리식 구조를 도메인별 마이크로서비스로 분리하는 시나리오입니다.

### 4.1 서비스 토폴로지 변화

**Before (Monolithic)**

```text
[ Client ] ➔ [ All-in-One Service (DB 공유) ]
```

**After (Microservices)**

```text
                   ┌─────────────┐
                   │ Order Service│ ➔ [Order DB]
                   └──────┬──────┘
                          │ HTTP / gRPC
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                 ↓
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│Product Service│   │Point Service │   │Coupon Service│
│ [Product DB] │   │ [User DB]    │   │ [Coupon DB]  │
└──────────────┘   └──────────────┘   └──────────────┘
```

각 서비스는 물리적으로 분리된 서버와 데이터베이스를 가지며, REST API 또는 gRPC를 통해 통신합니다.

-----

## 5\. 분산 환경의 트랜잭션 한계

서비스가 분리되면 **ACID 트랜잭션을 보장할 수 없습니다.** 단일 DB가 아니므로 `Commit`과 `Rollback`이 서비스별로 독립적으로 발생합니다.

### 5.1 분산 환경에서의 실패 시나리오

```text
1. Order 서비스 ➔ Product 서비스 호출 (재고 차감 ✅)
   - Product DB: 커밋됨

2. Order 서비스 ➔ Point 서비스 호출 (포인트 차감 ❌)
   - Point DB: 실패 및 롤백

3. 결과
   - Product DB: 재고 감소됨 (되돌릴 방법 없음)
   - Point DB: 잔액 그대로
   - Order DB: 주문 없음
```

**문제의 핵심**: 네트워크 너머의 다른 서비스 DB를 강제로 롤백할 수 없음 (2PC는 성능 문제로 지양).

-----

## 6\. MSA 전환 시: SAGA 패턴으로 확장

분산 환경에서도 현재의 하이브리드 패턴을 유지하되, 기술 스택을 MSA에 맞게 전환합니다.

### 6.1 Orchestration 영역의 전환

**Before (현재): Java 메서드 호출**
```java
// 같은 애플리케이션 내에서 직접 호출
productService.decreaseStock(...);
pointService.deductPoints(...);
couponService.use(...);
```

**After (MSA): HTTP/gRPC 호출 + SAGA 상태 관리**
```java
// Order Service가 Orchestrator
public void executePayment() {
    SagaState saga = sagaRepository.save(
        SagaState.create(orderId)
    );

    try {
        // HTTP 호출로 변경
        saga.addStep("DECREASE_STOCK");
        productClient.decreaseStock(...);  // REST API
        saga.completeStep("DECREASE_STOCK");

        saga.addStep("DEDUCT_POINTS");
        pointClient.deductPoints(...);     // REST API
        saga.completeStep("DEDUCT_POINTS");

    } catch (Exception e) {
        compensate(saga);  // 역순으로 보상
    }
}
```

**추가 요소**:
- **SAGA State Table**: 전체 흐름 추적
- **보상 데이터 저장**: 각 단계마다 롤백에 필요한 정보 보관
- **타임아웃 관리**: 네트워크 장애 대응

### 6.2 Choreography 영역의 전환

**Before (현재): Spring Event**
```java
@TransactionalEventListener
public void handleOrderConfirmed(OrderConfirmedEvent event) {
    // 같은 애플리케이션 내 비동기 처리
}
```

**After (MSA): Kafka 이벤트 스트리밍**
```text
┌──────────────┐ Publish  ┌──────────┐ Subscribe ┌──────────────┐
│Order Service │ ───────➔ │  Kafka   │ ───────➔ │Ranking Service│
└──────────────┘          │ (Topic)  │          └──────────────┘
                          └─────┬────┘
                                │ Subscribe
                                ↓
                          ┌──────────────┐
                          │Data Platform │
                          │   Service    │
                          └──────────────┘
```

**[이벤트 흐름 예시]**

```text
OrderCreated (Event) ➔ [Product Svc] 재고 차감
                     ➔ StockDecreased (Event)
                     ➔ [Point Svc] 포인트 차감
                     ➔ PointsDeducted (Event)
                     ➔ [Order Svc] 주문 확정
```

**[실패 및 보상 흐름]**

```text
... ➔ [Point Svc] 잔액 부족 (실패)
    ➔ PointDeductFailed (Event 발행)
    ➔ [Product Svc] 이벤트를 수신하여 재고 복구
    ➔ [Order Svc] 주문 취소 처리
```

### 6.3 패턴 선택 가이드

| 상황 | 권장 패턴 | 이유 |
|-----|---------|------|
| 결제, 주문 등 핵심 비즈니스 | **Orchestration** | 명확한 흐름 제어, 보상 관리 용이 |
| 통계, 알림, 로그 등 부가 기능 | **Choreography** | 느슨한 결합, 확장성 |
| 복잡한 워크플로우 (5단계 이상) | **Orchestration** | 중앙 추적 및 디버깅 |
| 단순한 이벤트 전파 | **Choreography** | 오버헤드 감소 |

**우리 시스템의 선택**: 현재처럼 **핵심은 Orchestration, 부가는 Choreography** 유지

-----

## 7\. 결론 및 로드맵

### 7.1 현주소 요약

* 현재 모놀리식 구조에서도 트랜잭션 분리로 인한 정합성 이슈가 존재합니다.
* 이를 **보상 트랜잭션 로직**과 **Outbox 패턴**을 통해 애플리케이션 레벨에서 해결하고 있습니다.

### 7.2 MSA 전환 로드맵

무리한 전면 전환보다는 단계적인 접근을 권장합니다.

**Phase 1 (현재)**: 모놀리식 내 하이브리드 패턴 강화
- ✅ Orchestration: 보상 트랜잭션 로직 검증
- ✅ Choreography: Spring Event 기반 비동기 처리
- ✅ Outbox 패턴으로 이벤트 신뢰성 확보

**Phase 2 (준비)**: 이벤트 인프라 전환
- Kafka 도입 (Spring Event → Kafka)
- 이벤트 스키마 정의 및 버저닝
- 분산 추적 체계 구축 (Zipkin, Jaeger)

**Phase 3 (실행)**: 점진적 서비스 분리
- `Product Service` 우선 분리 (트래픽 많음, 독립성 높음)
- Orchestration: REST API 호출 + SAGA 상태 관리
- Choreography: Kafka 이벤트 스트리밍

**Phase 4 (완료)**: 전체 MSA 전환
- 도메인별 완전 분리
- Service Mesh 도입 (Istio)
- 중앙 모니터링 및 장애 대응 체계 확립

### 7.3 핵심 원칙

**하이브리드 패턴 유지**
- 핵심 비즈니스 = Orchestration (제어와 신뢰성)
- 부가 기능 = Choreography (확장성과 유연성)

**점진적 전환**
- "기술을 위한 MSA"가 아닌 "비즈니스 문제 해결 수단"
- 현재의 보상 트랜잭션 로직이 SAGA 패턴의 기초

**관찰성 우선**
- 로깅, 모니터링, 추적 체계 먼저 구축
- 문제 발생 시 빠른 원인 파악이 핵심
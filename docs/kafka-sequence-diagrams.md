# Kafka 시퀀스 다이어그램

이 문서는 주문/결제 시스템과 쿠폰 시스템의 상세한 처리 흐름을 Mermaid 시퀀스 다이어그램으로 시각화합니다.

---

## 1. 주문/결제 시스템 플로우

### 1.1 주문 생성 및 Outbox 저장 (동기)

```mermaid
sequenceDiagram
    participant Client as 사용자
    participant API as Order API
    participant Service as OrderService
    participant DB as MySQL

    Client->>+API: POST /api/orders (주문 요청)
    API->>+Service: createOrder(request)

    Service->>Service: 주문 생성 (Order)
    Service->>Service: Outbox 생성 (PENDING)

    Service->>+DB: BEGIN TRANSACTION
    Service->>DB: INSERT INTO orders
    Service->>DB: INSERT INTO outbox (PENDING)
    Service->>DB: COMMIT
    DB-->>-Service: 트랜잭션 완료

    Service-->>-API: Order 반환
    API-->>-Client: 200 OK (1초 이내)

    Note over Client: 주문 접수 완료!<br/>백그라운드 처리 시작
```

### 1.2 Outbox Poller → Kafka 발행 (비동기)

```mermaid
sequenceDiagram
    participant Poller as Outbox Poller<br/>(Scheduler)
    participant DB as MySQL
    participant Producer as Kafka Producer
    participant Kafka as Kafka Broker

    loop 5초마다 실행
        Poller->>+DB: SELECT * FROM outbox<br/>WHERE status = 'PENDING'<br/>LIMIT 100
        DB-->>-Poller: 100건 반환

        alt 발행 대상 있음
            loop 각 Outbox 처리
                Poller->>+Producer: sendEvent(OrderConfirmedEvent)
                Producer->>+Kafka: SEND (key=orderId)
                Kafka-->>-Producer: ACK (offset=123)
                Producer-->>-Poller: 발행 성공

                Poller->>+DB: UPDATE outbox<br/>SET status = 'PUBLISHED'
                DB-->>-Poller: 업데이트 완료
            end
        else 발행 대상 없음
            Note over Poller: 대기 (5초 후 재시도)
        end
    end
```

### 1.3 Kafka Consumer 처리 (병렬, 독립적)

```mermaid
sequenceDiagram
    participant Kafka as Kafka Broker
    participant C1 as 알림 Consumer
    participant C2 as 데이터 플랫폼<br/>Consumer
    participant C3 as 로그 Consumer
    participant DB as MySQL
    participant External as 외부 시스템

    Note over Kafka: 메시지 파티셔닝<br/>(orderId % 3)

    par 병렬 처리 (독립적 Consumer Group)
        Kafka->>+C1: OrderConfirmedEvent
        C1->>External: 알림 전송 (FCM/Email)
        External-->>C1: 전송 완료
        C1->>Kafka: ACK (커밋)
        deactivate C1
    and
        Kafka->>+C2: OrderConfirmedEvent
        C2->>External: 데이터 전송<br/>(BigQuery/S3)
        External-->>C2: 전송 완료
        C2->>+DB: UPDATE outbox<br/>SET status = 'SUCCESS'
        DB-->>-C2: 업데이트 완료
        C2->>Kafka: ACK (커밋)
        deactivate C2
    and
        Kafka->>+C3: OrderConfirmedEvent
        C3->>DB: INSERT INTO logs
        DB-->>C3: 저장 완료
        C3->>Kafka: ACK (커밋)
        deactivate C3
    end

    Note over C1,C3: 각 Consumer는 독립적으로 동작<br/>한 Consumer의 실패가 다른 Consumer에 영향 없음
```

---

## 2. 쿠폰 시스템 플로우

### 2.1 쿠폰 발급 요청 및 Redis Stream 추가

```mermaid
sequenceDiagram
    participant Client as 사용자
    participant API as Coupon API
    participant Service as CouponQueueService
    participant Redis as Redis Stream

    Client->>+API: POST /api/coupons/{id}/issue
    API->>+Service: issueCoupon(userId, couponId)

    Service->>Service: 발급 대기 상태 생성
    Service->>+Redis: XADD coupon:stream:{couponId}<br/>{userId, couponId}
    Redis-->>-Service: messageId 반환

    Service-->>-API: 발급 대기 중
    API-->>-Client: 202 Accepted

    Note over Client: 발급 요청 완료!<br/>처리 중...
```

### 2.2 Redis Stream Consumer → DB 저장 → Kafka 발행

```mermaid
sequenceDiagram
    participant Consumer as CouponIssueConsumer<br/>(Scheduler)
    participant Redis as Redis Stream
    participant DB as MySQL
    participant Producer as Kafka Producer
    participant Kafka as Kafka Broker

    loop 100ms마다 실행
        Consumer->>+Redis: XREADGROUP<br/>coupon:stream:{couponId}<br/>(배치 10개)
        Redis-->>-Consumer: 10건 메시지 반환

        alt 메시지 있음
            loop 각 메시지 처리
                Consumer->>Consumer: 선착순 검증<br/>(issued < totalQuantity)

                alt 발급 가능
                    Consumer->>+DB: BEGIN TRANSACTION
                    Consumer->>DB: INSERT INTO user_coupon<br/>(UNIQUE 제약)
                    Consumer->>DB: UPDATE coupon<br/>SET issued_quantity = issued_quantity + 1
                    Consumer->>DB: COMMIT
                    DB-->>-Consumer: 저장 성공

                    Consumer->>Consumer: 결과 저장 (Redis)<br/>success=true

                    Consumer->>+Producer: publish(CouponIssuedEvent)
                    Producer->>+Kafka: SEND (key=userId)
                    Kafka-->>-Producer: ACK
                    Producer-->>-Consumer: 발행 성공

                    Consumer->>+Redis: XACK (메시지 커밋)
                    Redis-->>-Consumer: ACK 완료
                else 발급 불가 (마감/중복)
                    Consumer->>Consumer: 결과 저장 (Redis)<br/>success=false
                    Consumer->>Redis: XACK (메시지 커밋)
                end
            end
        else 메시지 없음
            Note over Consumer: 대기 (100ms 후 재시도)
        end
    end
```

### 2.3 Kafka Consumer 처리 (병렬)

```mermaid
sequenceDiagram
    participant Kafka as Kafka Broker
    participant C1 as 알림 Consumer
    participant C2 as 로그 Consumer
    participant External as 외부 시스템
    participant DB as MySQL

    Note over Kafka: 메시지 파티셔닝<br/>(userId % 3)

    par 병렬 처리 (독립적 Consumer Group)
        Kafka->>+C1: CouponIssuedEvent
        C1->>External: 쿠폰 발급 알림<br/>(SMS/Push)
        External-->>C1: 전송 완료
        C1->>Kafka: ACK (커밋)
        deactivate C1
    and
        Kafka->>+C2: CouponIssuedEvent
        C2->>DB: INSERT INTO coupon_logs
        DB-->>C2: 저장 완료
        C2->>Kafka: ACK (커밋)
        deactivate C2
    end

    Note over C1,C2: 알림과 로그는 독립적으로 처리<br/>Best Effort 전략
```

---

## 3. 순차 처리 전략: 콘서트 대기열 (참고용)

### 3.1 대기열 진입 및 순차 처리

```mermaid
sequenceDiagram
    participant Client1 as 사용자 1
    participant Client2 as 사용자 2
    participant Client3 as 사용자 3
    participant API as Queue API
    participant Producer as Kafka Producer
    participant Kafka as Kafka<br/>(파티션 1개)
    participant Consumer as Queue Consumer<br/>(단일)
    participant Service as EntranceService

    Note over Kafka: ⚠️ 파티션 1개로 고정<br/>→ FIFO 순서 보장

    Client1->>+API: POST /api/queue/enter
    API->>+Producer: send(QueueEvent, user=1)
    Producer->>Kafka: SEND (offset=100)
    Producer-->>-API: 대기 순번: 100
    API-->>-Client1: 100번 대기 중

    Client2->>+API: POST /api/queue/enter
    API->>+Producer: send(QueueEvent, user=2)
    Producer->>Kafka: SEND (offset=101)
    Producer-->>-API: 대기 순번: 101
    API-->>-Client2: 101번 대기 중

    Client3->>+API: POST /api/queue/enter
    API->>+Producer: send(QueueEvent, user=3)
    Producer->>Kafka: SEND (offset=102)
    Producer-->>-API: 대기 순번: 102
    API-->>-Client3: 102번 대기 중

    Note over Consumer: 단일 Consumer가 순차 처리<br/>→ 순서 보장

    loop 순차 처리
        Consumer->>+Kafka: poll() (offset=100)
        Kafka-->>-Consumer: QueueEvent (user=1)
        Consumer->>+Service: processEntrance(user=1)
        Service-->>-Consumer: 입장 완료
        Consumer->>Kafka: ACK (커밋)

        Consumer->>+Kafka: poll() (offset=101)
        Kafka-->>-Consumer: QueueEvent (user=2)
        Consumer->>+Service: processEntrance(user=2)
        Service-->>-Consumer: 입장 완료
        Consumer->>Kafka: ACK (커밋)

        Consumer->>+Kafka: poll() (offset=102)
        Kafka-->>-Consumer: QueueEvent (user=3)
        Consumer->>+Service: processEntrance(user=3)
        Service-->>-Consumer: 입장 완료
        Consumer->>Kafka: ACK (커밋)
    end

    Note over Consumer: 순서대로 처리 완료<br/>1 → 2 → 3
```

---

## 4. 장애 시나리오

### 4.1 Kafka Consumer 실패 및 재시도

```mermaid
sequenceDiagram
    participant Kafka as Kafka Broker
    participant Consumer as Data Platform<br/>Consumer
    participant External as 외부 API

    loop 메시지 처리
        Consumer->>+Kafka: poll()
        Kafka-->>-Consumer: OrderConfirmedEvent<br/>(offset=100)

        Consumer->>+External: POST /api/data<br/>(데이터 전송)
        External-->>-Consumer: ❌ 500 Error<br/>(타임아웃)

        Note over Consumer: 커밋하지 않음<br/>→ 재시도 대상

        Note over Consumer: 대기 (1초 후 재시도)

        Consumer->>+Kafka: poll()
        Kafka-->>-Consumer: OrderConfirmedEvent<br/>(offset=100) ← 동일 메시지

        Consumer->>+External: POST /api/data<br/>(재시도)
        External-->>-Consumer: ✅ 200 OK

        Consumer->>Kafka: ACK (커밋)

        Note over Consumer: 다음 메시지 처리 (offset=101)
    end
```

### 4.2 Outbox Poller 실패 및 재시도

```mermaid
sequenceDiagram
    participant Poller as Outbox Poller
    participant DB as MySQL
    participant Kafka as Kafka Broker
    participant Scheduler as Retry Scheduler

    Poller->>+DB: SELECT outbox (PENDING)
    DB-->>-Poller: outboxId=1 반환

    Poller->>+Kafka: SEND event
    Kafka-->>-Poller: ❌ Broker 장애

    Poller->>+DB: UPDATE outbox<br/>SET status = 'FAILED'<br/>error = 'Kafka 장애'
    DB-->>-Poller: 업데이트 완료

    Note over Scheduler: 1분 후 재시도 스케줄러 실행

    Scheduler->>+DB: SELECT outbox<br/>WHERE status = 'FAILED'<br/>AND retryCount < 10
    DB-->>-Scheduler: outboxId=1 반환

    Scheduler->>+DB: UPDATE outbox<br/>SET status = 'PENDING'<br/>retryCount = retryCount + 1
    DB-->>-Scheduler: 업데이트 완료

    Note over Poller: 5초 후 Poller가 다시 처리

    Poller->>+DB: SELECT outbox (PENDING)
    DB-->>-Poller: outboxId=1 반환 (재시도)

    Poller->>+Kafka: SEND event
    Kafka-->>-Poller: ✅ ACK (성공)

    Poller->>+DB: UPDATE outbox<br/>SET status = 'PUBLISHED'
    DB-->>-Poller: 완료
```

---

## 5. 플로우 비교: AS-IS vs TO-BE

### 5.1 AS-IS (동기 처리)

```mermaid
sequenceDiagram
    participant Client as 사용자
    participant API as Order API
    participant Service as OrderService
    participant DB as MySQL
    participant Noti as 알림 서비스
    participant Data as 데이터 플랫폼

    Client->>+API: POST /orders
    API->>+Service: createOrder()

    Service->>+DB: INSERT orders
    DB-->>-Service: 저장 완료

    Service->>+Noti: sendNotification()
    Note over Noti: ❌ 알림 서비스 지연 1초
    Noti-->>-Service: 전송 완료

    Service->>+Data: sendData()
    Note over Data: ❌ 데이터 플랫폼 지연 1초
    Data-->>-Service: 전송 완료

    Service-->>-API: 주문 완료
    API-->>-Client: 200 OK (총 3초)

    Note over Client: ❌ 응답 지연<br/>❌ 알림 실패 시 주문 롤백<br/>❌ 장애 전파
```

### 5.2 TO-BE (비동기 처리)

```mermaid
sequenceDiagram
    participant Client as 사용자
    participant API as Order API
    participant Service as OrderService
    participant DB as MySQL
    participant Poller as Outbox Poller
    participant Kafka as Kafka
    participant C1 as 알림 Consumer
    participant C2 as 데이터 Consumer

    Client->>+API: POST /orders
    API->>+Service: createOrder()

    Service->>+DB: INSERT orders + outbox
    DB-->>-Service: 저장 완료

    Service-->>-API: 주문 완료
    API-->>-Client: ✅ 200 OK (1초)

    Note over Client: ✅ 빠른 응답<br/>✅ 장애 격리

    Note over Poller,Kafka: 백그라운드 처리 (비동기)

    Poller->>DB: SELECT outbox (PENDING)
    DB-->>Poller: outbox 반환
    Poller->>Kafka: SEND event
    Kafka-->>Poller: ACK

    par 병렬 처리 (독립적)
        Kafka->>C1: 메시지 전달
        Note over C1: 알림 실패해도<br/>주문은 유지
    and
        Kafka->>C2: 메시지 전달
        Note over C2: 데이터 전송 지연해도<br/>주문은 유지
    end
```
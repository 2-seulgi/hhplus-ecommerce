# 시퀀스 다이어그램 (주요 로직)

## FR-PAY-01. 결제 처리

```mermaid
sequenceDiagram
    actor User as 사용자
    participant PC as PaymentController
    participant PS as PaymentService
    participant OR as OrderRepository
    participant UCR as UserCouponRepository
    participant PR as ProductRepository
    participant PTR as PointRepository
    participant ODR as OrderDiscountRepository
    participant OB as OutboxRepository
    participant DB as Database

    User->>PC: POST /users/{userId}/orders/{orderId}/payment {couponCode?}
    activate PC
    Note over PC: Idempotency-Key 헤더 조회<br/>(기존 성공 응답 있으면 즉시 반환)

    PC->>PS: processPayment(userId, orderId, couponCode?)
    activate PS
    Note over PS: === 트랜잭션 시작 ===

    Note over PS: 1) 주문 조회/검증<br/>(주문 소유자, 상태==PENDING, 만료 여부)
    PS->>OR: findByIdWithItems(orderId)
    activate OR
    OR->>DB: 주문 + 항목 조회
    DB-->>OR: Order(+items, snapshotAmount)
    OR-->>PS: Order
    deactivate OR

    alt 사용자 불일치
        Note over PS: === 트랜잭션 롤백 ===
        PS-->>PC: 404 ORDER_NOT_FOUND
        PC-->>User: 주문을 찾을 수 없습니다
    end
    alt 상태 != PENDING
        Note over PS: === 트랜잭션 롤백 ===
        PS-->>PC: 409 INVALID_ORDER_STATUS
        PC-->>User: 결제 가능한 주문이 아닙니다
    end
    alt 주문 만료
        Note over PS: now > expiresAt
        Note over PS: === 트랜잭션 롤백 ===
        PS-->>PC: 409 ORDER_EXPIRED
        PC-->>User: 주문이 만료되었습니다
    end

    Note over PS: 2) 쿠폰 검증 (선택)
    alt 쿠폰 코드가 제공된 경우
        PS->>UCR: findByCodeAndUserId(couponCode, userId)
        activate UCR
        UCR->>DB: 사용자 쿠폰 조회
        DB-->>UCR: UserCoupon
        UCR-->>PS: UserCoupon
        deactivate UCR

        alt 쿠폰 검증 실패
            Note over PS: used==true OR<br/>now < useStartAt OR<br/>now > useEndAt
            Note over PS: === 트랜잭션 롤백 ===
            PS-->>PC: 409 COUPON_INVALID
            PC-->>User: 사용 불가능한 쿠폰입니다
        end
        Note over PS: 할인 금액 계산<br/>discount = min(couponValue, snapshotAmount)
    end

    Note over PS: 3) 재고 차감 (항목 루프, 낙관적 락)
    loop 각 orderItem
        PS->>PR: decreaseStock(productId, quantity, version)
        activate PR
        PR->>DB: UPDATE product<br/>SET stock=stock-qty, version=version+1<br/>WHERE id=? AND version=? AND stock>=qty
        DB-->>PR: rowCount (1=성공, 0=실패)
        PR-->>PS: boolean success
        deactivate PR

        alt 재고 차감 실패
            Note over PS: version 불일치 OR 재고 부족
            Note over PS: === 트랜잭션 롤백 ===
            PS-->>PC: 409 OUT_OF_STOCK
            PC-->>User: 재고가 부족합니다
        end
    end

    Note over PS: 4) 최종 금액 계산<br/>finalAmount = snapshotAmount - discount

    Note over PS: 5) 포인트 차감 (원자적 1회, 낙관적 락)
    PS->>PTR: decreaseBalance(userId, finalAmount, version)
    activate PTR
    Note over PTR: 5-1) 잔액 검증 + 차감
    PTR->>DB: UPDATE user<br/>SET balance=balance-amount, version=version+1<br/>WHERE id=? AND version=? AND balance>=amount
    DB-->>PTR: rowCount + newBalance

    alt 업데이트 실패 (rowCount=0)
        Note over PTR: version 불일치 OR 잔액 부족
        PTR-->>PS: failure
        Note over PS: === 트랜잭션 롤백 ===
        PS-->>PC: 402 INSUFFICIENT_POINT
        PC-->>User: 포인트가 부족합니다
    end

    Note over PTR: 5-2) 포인트 히스토리 기록
    PTR->>DB: INSERT INTO point<br/>(user_id, point_type, amount, balance_after)<br/>VALUES (userId, 'USE', finalAmount, newBalance)
    PTR-->>PS: remainingBalance
    deactivate PTR

    alt 쿠폰 사용 처리
        Note over PS: 6) 쿠폰 사용 처리
        PS->>UCR: markAsUsed(userCouponId)
        activate UCR
        UCR->>DB: UPDATE user_coupon SET used=true
        deactivate UCR

        PS->>ODR: save(orderDiscount)
        activate ODR
        ODR->>DB: INSERT INTO order_discount
        deactivate ODR
    end

    Note over PS: 7) 주문 상태 변경
    PS->>OR: updateStatus(orderId, CONFIRMED)
    activate OR
    OR->>DB: UPDATE order SET status='CONFIRMED'
    deactivate OR

    Note over PS: 8) Outbox 이벤트 기록
    PS->>OB: save(outbox)
    activate OB
    Note over OB: 데이터 플랫폼 전송용<br/>이벤트 기록
    OB->>DB: INSERT INTO outbox
    deactivate OB

    Note over PS: === 트랜잭션 커밋 ===

    PS-->>PC: PaymentResponse(orderId, remainingBalance)
    deactivate PS

    Note over PC: Idempotency-Key에 최종 응답 저장<br/>(Redis, TTL=1h)
    PC-->>User: 200 OK (결제 완료)
    deactivate PC
```

## FR-CPN-01. 선착순 쿠폰 발급

```mermaid
sequenceDiagram
    actor User as 사용자
    participant CC as CouponController
    participant CS as CouponService
    participant CPR as CouponRepository
    participant UCR as UserCouponRepository
    participant DB as Database

    User->>CC: POST /users/{userId}/coupons/{couponId}/issue
    activate CC

    CC->>CS: issueCoupon(userId, couponId)
    activate CS

    Note over CS: === 트랜잭션 시작 ===

    Note over CS: 1. 발급 수량 증가 (낙관적 락)
    CS->>CPR: increaseIssued(couponId, version)
    activate CPR
    Note over CPR: 낙관적 락으로<br/>수량 증가
    CPR->>DB: 발급 수량 증가
    DB-->>CPR: 성공 또는 실패
    deactivate CPR

    alt 발급 불가
        Note over CS: version 불일치 또는<br/>수량 소진
        Note over CS: === 트랜잭션 롤백 ===
        CS-->>CC: 409 SOLD_OUT
        CC-->>User: 쿠폰이 모두 소진되었습니다
    end

    Note over CS: 2. 사용자 쿠폰 발급
    CS->>UCR: issueToUser(userId, couponId)
    activate UCR
    UCR->>DB: 사용자 쿠폰 생성

    alt 중복 발급
        DB-->>UCR: UNIQUE 제약 위반
        Note over CS: === 트랜잭션 롤백 ===
        CS-->>CC: 409 ALREADY_ISSUED
        CC-->>User: 이미 발급받은 쿠폰입니다
    else 발급 성공
        DB-->>UCR: 성공
        deactivate UCR
        Note over CS: === 트랜잭션 커밋 ===
        CS-->>CC: 200 OK
        CC-->>User: 쿠폰 발급 완료
    end

    deactivate CS
    deactivate CC
```

## FR-ORD-02. 주문 만료 처리 (스케줄러)

```mermaid
sequenceDiagram
    participant Scheduler as OrderExpirationScheduler
    participant OS as OrderService
    participant OR as OrderRepository
    participant DB as Database

    Note over Scheduler: 매 1분마다 실행

    Scheduler->>OS: expireOrders()
    activate OS

    OS->>OR: findExpiredPendingOrders(now)
    activate OR
    OR->>DB: 만료 대상 조회
    DB-->>OR: List<Order>
    deactivate OR

    alt 만료 대상 없음
        OS-->>Scheduler: 종료
    else 만료 대상 존재
        loop 각 만료된 주문
            Note over OS: === 트랜잭션 시작 ===

            OS->>OR: updateStatus(orderId, CANCELLED)
            activate OR
            OR->>DB: 주문 취소
            deactivate OR

            Note over OS: === 트랜잭션 커밋 ===
            Note over OS: 재고 영향 없음
        end

        OS-->>Scheduler: 처리 완료
    end
    deactivate OS
```
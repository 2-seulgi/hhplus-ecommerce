# 동시성 문제 분석 및 해결 방안

## 1. 문제 식별

현재 이커머스 시스템에서는 주문/결제, 쿠폰, 포인트, 재고 관리 등 여러 기능이 동시에 동작한다.
여러 사용자가 동시에 같은 리소스(Product, User, Coupon 등)에 접근하면서 Race Condition 이 발생할 수 있고, 이는 곧 데이터 정합성(Data Consistency) 훼손으로 이어진다.

아래에서는 시스템 내에서 실제로 발생 가능한 대표적인 동시성 문제를 정리하고, 그 영향도를 함께 기술했다.

### 1.1 재고 차감 (Product.stock)

#### A. Overselling 문제 (재고 음수 / 초과 판매) 
검증(stock >= quantity)이 오래된 값을 기준으로 수행되어, 여러 트랜잭션이 동시에 재고 차감 로직을 통과해 버리는 현상.

```java
// Product.java
public void decreaseStock(int quantity) {
    if (this.stock < quantity) {
        throw new BusinessException("재고가 부족합니다", "OUT_OF_STOCK");
    }
    this.stock -= quantity;  // ⚠️ Race Condition!
}
```

**시나리오:**
1. 특정 상품의 재고가 1개 남은 상황에서
2. 사용자 A, B가 동시에 주문 시도
3. 두 트랜잭션 모두 stock 조회 시점에는 stock = 1 이므로 검증을 통과
4. 이후 각각 this.stock -= quantity 연산을 수행한다.
5. **결과적으로 재고가 0이 아니라 -1 등 음수로 내려갈 수 있다.**

#### B. Lost Update 문제 (업데이트 덮어쓰기)
각 트랜잭션이 계산한 재고 값이 서로 덮어써져, 중간 업데이트가 사라지는 현상.

```java
// Product.java
public void decreaseStock(int quantity) {
    if (this.stock < quantity) {
        throw new BusinessException("재고가 부족합니다", "OUT_OF_STOCK");
    }
    this.stock -= quantity;
    this.updatedAt = Instant.now();
}
```
**시나리오:**
1. 특정 상품의 재고가 5개 남은 상황에서
2. Transaction A: stock 조회 → 5, Transaction B: stock 조회 → 5
3. Transaction A: stock -= 1 → stock=4
4. Transaction B: stock -= 2 → stock=3
5. 두 UPDATE가 순서대로 반영되면서 최종 재고 = 3
   (실제로 기대하는 값은 5 - 1 - 2 = 2) 
즉, 중간 업데이트가 사라지는 Lost Update 가 발생한다.

**영향도:**
- 🔴 **Critical**
  - 실제 보유 재고보다 많은 수량이 판매되어 배송 불가, 주문 취소 등의 문제가 발생한다
  - 운영자가 수작업으로 재고/주문을 조정해야 하며, 이는 고객 불만, 신뢰도 하락으로 이어질 수 있다.
  - 재고 데이터가 한 번 깨지면 이후 개발 될 수 있는 통계나, 추천 로직 등 2차 기능에도 악영향을 준다.

---

### 1.2 포인트 차감 (User.balance)
```java
// User.java
public void use(int amount) {
    if (this.balance < amount) {
        throw new InsufficientBalanceException("잔액이 부족합니다");
    }
    this.balance -= amount;  // ⚠️ Race Condition?
}
```

**시나리오:**
1. 사용자 잔액이 10,000원인 상태에서
2. 동일 사용자가 두 개의 주문을 거의 동시에 시도한다. (각 주문 금액 7,000원)
3. 두 트랜잭션 모두 this.balance < amount 검증을 통과한다. (검증 시점에는 balance=10,000)
4. 각 트랜잭션에서 this.balance -= amount 연산을 수행한다.
5. **이론적으로 잔액이 -4,000원처럼 음수로 내려갈 수 있다.**

**영향도:**
- 🟡 **Medium**
  - 금전 관련 데이터 정합성 문제이지만, 실제 발생 가능성은 낮음
  - 실제 서비스 흐름(장바구니 → 주문 생성 → 결제) 상,
    동일 사용자가 동시에 여러 주문을 결제하는 경우는 거의 없음
  - 이슈의 본질도 "포인트 동시 차감" 자체보다는 "동일 주문의 중복 결제"에 더 가까움
  - 주문 레벨에서 중복 결제를 방지하면 포인트 차감도 자연스럽게 1회만 발생

---

### 1.3 쿠폰 발급 (Coupon.issuedQuantity)
```java
// Coupon.java
public void increaseIssuedQuantity() {
    if (this.issuedQuantity >= this.totalQuantity) {
        throw new BusinessException("쿠폰이 모두 소진되었습니다", "COUPON_SOLD_OUT");
    }
    this.issuedQuantity++;  // ⚠️ Race Condition!
}
```

**시나리오:**
1. “선착순 100명 한정 쿠폰” totalQuantity = 100
2. 현재까지 발급 수량 issuedQuantity = 99
3. 이벤트 페이지에 트래픽이 몰려 동시에 수백~수천 명이 발급 요청을 보낸다.
4. 여러 트랜잭션이 동시에 issuedQuantity < totalQuantity 조건을 통과한다.
5. 각 트랜잭션이 issuedQuantity++를 수행한 결과,
6. **최종적으로 issuedQuantity가 100을 초과하여 101, 102 등으로 증가할 수 있다.**

**영향도:**
- 🟠 **High**
  - “선착순 N명” 이벤트의 핵심 조건이 깨져 마케팅 신뢰도가 떨어진다.
  - 예상보다 많은 쿠폰이 발급되면 비용 초과 및 정산 이슈로 이어질 수 있다.

**현재 상태:**
- 초기에는 낙관적 락(Optimistic Lock) + 재시도(Retry) 패턴을 적용했으나, 선착순 이벤트 특성상 충돌이 매우 잦아 재시도가 빈번하게 발생할 수 있다.
- “충돌이 자주 일어난다”는 가정이 더 현실적이므로,
  비관적 락(Pessimistic Lock + 짧은 임계 구역) 이 더 적합하다고 판단했다. (→ 4.3에서 상세 설명)
---

## 2. 원인 분석

### 2.1 Race Condition

**정의:**
Race Condition은 여러 트랜잭션이 동일한 공유 자원을 동시에 갱신할 때
실행 순서에 따라 결과가 달라지는 현상을 의미한다.
특히 “조회 후 수정(SELECT → UPDATE)” 패턴에서 대표적으로 발생한다.

**발생 원인:**
```
Transaction A          Transaction B
─────────────          ─────────────
READ stock = 5
                       READ stock = 5
UPDATE stock = 4
                       UPDATE stock = 4  ← Lost Update!
```
위와 같이 두 트랜잭션이 같은 초기값(5)을 보고 각자 계산한 값을 저장하면서,
중간에 반영되었던 변경 내용이 덮어씌워지는 것이 Lost Update 문제다.

---

### 2.2 트랜잭션 격리 수준

**MySQL 기본 격리 수준: REPEATABLE READ**

| 격리 수준               | Dirty Read | Non-Repeatable Read | Phantom Read | Lost Update |
|---------------------|------------|---------------------|--------------|-------------|
| READ UNCOMMITTED    | O          | O                   | O            | O           |
| READ COMMITTED      | X          | O                   | O            | O           |
| **REPEATABLE READ** | X          | X                   | O            | **O**       |
| SERIALIZABLE        | X          | X                   | X            | X           |

즉, 
- REPEATABLE READ라 하더라도 Lost Update는 기본적으로 방지되지 않는다.
- 명시적인 락(pessimistic) 또는 버전 관리(optimistic)가 별도로 필요하다.

---

### 2.3 JPA/Hibernate의 기본 동작과의 조합
JPA의 전형적인 흐름은 다음과 같다.
```java
// 1. 조회
Product product = productRepository.findById(productId);

// 2. 비즈니스 로직 (순수 Java 객체 수정)
product.decreaseStock(quantity);

// 3. 트랜잭션 커밋 시점에 UPDATE 실행
// UPDATE product SET stock = stock - ? WHERE id = ?
```
조회(SELECT)와 수정(UPDATE) 사이에
다른 트랜잭션이 끼어들 수 있기 때문에,
JPA만으로는 기본적으로 Lost Update를 막지 못한다.

---

## 3. 해결 방안 비교

### 3.1 Optimistic Lock (낙관적 락)

**개념:**
- 충돌이 **드물 것**으로 가정
- 엔티티에 버전 컬럼(@Version)을 두고, 갱신 시점에 충돌 여부를 검증
- 충돌 시 예외 발생 → 애플리케이션 레벨 재시도

**동작 방식:**
```sql
-- 조회
SELECT * FROM product WHERE id = 1;
-- stock = 5, version = 10

-- 수정 (version 체크)
UPDATE product
SET stock = 4, version = 11
WHERE id = 1 AND version = 10;

-- 다른 트랜잭션이 먼저 수정했다면 affected rows = 0
-- → OptimisticLockException 발생
```

**장점:**
- 실질적으로는 락을 걸지 않아 성능에 유리
- 데드락 발생 없음

**단점:**
- 충돌 시 재시도 필요 (애플리케이션 로직 복잡)
- 충돌이 빈번하면 성능 저하
- 재시도 횟수 제한 필요 백오프 전략을 별도로 설계해야 함

**적합한 경우:**
- 충돌 빈도가 낮음
- 조회가 빈번하고 갱신이 드문 경우

---

### 3.2 Pessimistic Lock (비관적 락)

**개념:**
- 충돌이 **빈번할 것**으로 가정
- 조회 시점에 DB 레벨의 락을 즉시 획득하여 다른 트랜잭션의 수정 및 읽기를 차단

```sql
-- 조회 + 락 획득
SELECT * FROM product WHERE id = 1 FOR UPDATE;

-- 다른 트랜잭션은 이 행에 대해 대기
-- 트랜잭션 커밋 시 락 해제
UPDATE product SET stock = 4 WHERE id = 1;
COMMIT;
```

**장점:**
- 충돌 자체를 방지 (재시도 불필요)
- 정합성 측면에서는 가장 직관적이고 안전
- 짧은 임계구역에서 효과적

**단점:**
- 락 대기로 성능 저하
- 데드락 발생 가능
- 읽기 작업도 차단될 수 있음

**적합한 경우:**
- 충돌 빈도가 높음 -> 경쟁이 잦은 공유 자원 (선착순 이벤트, 인기 상품 재고 등)
- 비즈니스적으로 정합성이 매우 중요한 영역
- 재시도 비용이 크거나, 재시도 자체가 바람직하지 않은 경우

---

### 3.3 분산 락 (Redis, Redisson)

**개념:**
- 외부 저장소(Redis)를 사용한 락
- 여러 애플리케이션 인스턴스 간에 동기화 가능

**적합한 경우:**
- 다중 인스턴스 환경에서 DB 외의 리소스까지 함께 보호해야 할 때
- DB뿐 아니라 외부 시스템도 동시에 보호해야 할 때
- 복잡한 비즈니스 로직

---

## 4. 선택한 해결 방안

### 4.1 재고 차감: Pessimistic Lock (비관적 락)

**선택 이유:**
1. 인기 상품에 대해서는 동시 주문이 충분히 자주 발생할 수 있다.
2. 재고 검증 → 주문 생성 → 결제까지 흐름이 길어, 단순 재시도만으로는 복잡도가 커진다.
3. 재고는 한 번 어긋나면 운영 리스크가 크다.
4. 임계 구역(재고 차감 구간)은 비교적 짧기 때문에 비관적 락 비용을 감수할 만하다.

**구현 방법:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") Long id);
```
여러 상품을 동시에 주문할 때 락 획득 순서가 달라 데드락이 발생할 수 있으므로,
아래와 같이 productId 기준 정렬 후 순차적으로 락을 잡는다.

```java
// ProductService.decreaseStocks()
List<OrderItem> sortedItems = orderItems.stream()
        .sorted(Comparator.comparing(OrderItem::getProductId))
        .toList();
```
예: 주문A [1,2,3], 주문B [3,1,5] → 둘 다 [1,3,5] 순서로 락 획득

**트레이드오프:**
- 동시성 처리량은 일부 희생되지만, 재고 정합성을 우선하는 방향으로 설계했다.

---

### 4.2 중복 결제 방지: Order Entity에 Optimistic Lock

**고민 과정:**
처음에는 User.balance(포인트)에 낙관적 락 + 재시도 로직을 고려했지만,
실제 플로우를 보면서 “실제 위험은 포인트 동시 차감이 아니라 ‘동일 주문의 중복 결제’다” 라는 점을 명확히 했다.

그래서 포인트가 아니라 Order 엔티티에 낙관적 락을 적용하는 쪽으로 방향을 잡았다.

```java
// Order.java
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Version   // ✅ 낙관적 락 적용
    private int version;

    // ...
}

// ProcessPaymentUseCase.java
@Transactional
public PaymentResult execute(PaymentCommand command) {
    Instant now = Instant.now(clock);

    // 1. 주문 검증 (PENDING 상태인지 등)
    Order order = orderService.validateForPayment(userId, orderId, now);

    // 2. 재고 차감 (Pessimistic Lock)
    productService.decreaseStocks(items);

    // 3. 포인트 차감 (User.balance – 별도 락 없음)
    User user = pointService.deductPoints(userId, finalAmount);

    // 4. 주문 확정 (PENDING → CONFIRMED)
    //    이 시점에서 version 검증 발생
    orderService.confirmOrder(order, finalAmount, now);

    return PaymentResult.from(order, user, discountAmount);
}

```

**동작 시나리오 (동일 주문 2번 결제 시도)**
```
시나리오: 동일 주문에 대해 2번의 결제 시도

Thread 1                          Thread 2
────────────────────              ────────────────────
1. Order 조회 (version=0)
                                  2. Order 조회 (version=0)
3. 재고 차감
4. 포인트 차감
5. Order.confirm()
   status: PENDING→CONFIRMED
   UPDATE ... WHERE
     id=? AND version=0
   → version=1 (성공)
                                  6. 재고 차감
                                  7. 포인트 차감
                                  8. Order.confirm()
                                     status: CONFIRMED (검증 실패)
                                     또는 version=0 체크 실패
                                     → OptimisticLockException
                                     → 전체 트랜잭션 롤백
                                     → 재고/포인트 모두 롤백됨
```
- Thread 1 이 먼저 결제 성공 → Order.version 증가, 상태 CONFIRMED
- Thread 2 는 커밋 시점에 OptimisticLockException 또는 상태 검증 실패
- Thread 2 트랜잭션 전체 롤백 → 재고/포인트 변경도 함께 롤백

**장점**
- @Transactional 로 전체 결제 플로우를 감싸기 때문에 롤백이 자연스럽게 처리된다.
- “이미 결제된 주문입니다” 라는 도메인 에러로 매핑 가능
- User.balance에 락을 걸지 않아도 되고, Retry 로직도 제거 가능 → 구조가 단순해짐

**테스트 검증:**
```java
@Test
@DisplayName("중복 결제 방지 - 동일 주문에 대한 동시 결제 시도 시 1건만 성공")
void concurrency_PreventDuplicatePayment_SameOrder() {
    // Given: 1개 주문 생성
    Long orderId = createOrder(testUser, testProduct, 2);

    // When: 동일 주문에 대해 10번 동시 결제 시도
    // Then: 1건만 성공, 나머지 9건 실패
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failCount.get()).isEqualTo(9);

    // 재고는 주문 수량만큼만 차감 (2개만)
    // 포인트는 1번만 차감 (100,000원만)
}
```

---

### 4.3 쿠폰 발급: Pessimistic Lock (비관적 락)
선착순 쿠폰 발급은 선착순 이벤트 특성상 충돌 빈도가 매우 높고, 
“한 번 더 발급해도 상관없다” 수준의 문제가 아니라
비용/마케팅 신뢰와 직결되는 영역이기 때문에 비관적 락을 선택했다.

```java
  // CouponJpaRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Coupon c WHERE c.id = :id")
Optional<Coupon> findByIdForUpdate(@Param("id") Long id);
```
비관적 락을 적용하면 다음과 같은 흐름이 보장된다.
1. 먼저 락을 획득한 트랜잭션만 쿠폰 발급 수량 증가(issuedQuantity++) 가능
2. 나중에 들어오는 트랜잭션은 앞선 트랜잭션이 커밋될 때까지 대기
3. 한 번에 하나의 요청만 발급 수량 증가 작업을 수행 → 초과 발급(over-issue) 방지

### 사용자별 중복 발급 방지: UNIQUE 제약 적용
또한, 사용자별 중복 발급을 막기 위해
`user_coupon` 테이블의 `(user_id, coupon_id)` 에 UNIQUE 제약을 두었다.
```java
// 사용자별 중복 발급을 막기 위해 UserCoupon Entity - UNIQUE 제약조건 추가
@UniqueConstraint(name = "uk_user_coupon", columnNames = {"userId", "couponId"})
```
이 제약은 다음 비즈니스 규칙을 강하게 보장한다.
- 동일 사용자는 동일 쿠폰을 1회만 발급 가능

**트레이드오프:**
- 장점 : 재시도 로직 불필요, 초과 발급 완전 차단, 코드 단순화, UNIQUE 제약으로 중복 발급도 DB 레벨에서 확실하게 방지
- 단점 : 락 대기 시간이 발생하지만, 선착순 이벤트 특성상 순차 처리되는 것이 오히려 자연스러움

**데드락 방지 불필요:**
재고 차감과 달리, 쿠폰 발급은 단일 쿠폰에 대해서만 락을 획득하므로
데드락이 발생할 수 없다. (데드락은 2개 이상의 락을 서로 다른 순서로 획득할 때 발생)

### 확장 가능성: 1인 N장 발급, 기간별 반복 지급
다만 모든 쿠폰 정책이 1인 1회 발급을 전제로 하지는 않는다.
향후 아래 같은 시나리오가 요구될 수 있다.
- 특정 쿠폰을 1인 N장 발급 (예: 친구 초대 쿠폰) → 쿠폰 엔티티에 perUserLimit 같은 정책 필드 추가
  - perUserLimit = 1 → UNIQUE 적용
  - perUserLimit > 1 또는 "하루 1회 제한" -> 다른 제약 방식 선택
   
---

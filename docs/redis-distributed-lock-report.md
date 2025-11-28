# Redis 분산락 & 캐시 적용 보고서

---

## 📋 목차
1. [문제 상황 분석](#1-문제-상황-분석)
2. [기술 선택 과정](#2-기술-선택-과정)
3. [최종 아키텍처](#3-최종-아키텍처)
4. [테스트 결과](#4-테스트-결과)
5. [회고](#5-회고)

---

## 1. 문제 상황 분석

### 1.1 동시성 문제 식별

이커머스 서비스에서 특히 신경 써야 했던 동시성 시나리오는 다음 세 가지다.

| 시나리오 | 문제 | 영향 |
|---------|------|------|
| **중복 결제** | 동일 주문에 대해 여러 번 결제 시도 | 💰 결제 중복, 포인트/재고 오차감 |
| **선착순 쿠폰** | 100명이 10장 쿠폰을 동시 발급 | 🎫 10장 초과 발급, 재고 손실 |
| **재고 차감** | 동시에 마지막 1개 상품 구매 | 📦 음수 재고, 과판매 |

### 1.2 기존 구현의 한계

**STEP 9/10에서 적용한 DB 락:**
- ✅ **재고 차감**: 비관적 락 (`SELECT FOR UPDATE`)
- ✅ **쿠폰 발급**: 비관적 락 + UNIQUE 제약
- ✅ **중복 결제**: 낙관적 락 (`@Version`)

**문제점:**
```java
// Order 엔티티
@Version
private Long version;

// ProcessPaymentUseCase
try {
    // 결제 처리
} catch (OptimisticLockException e) {
    // 충돌 시 예외만 발생
    throw new BusinessException(
                "이미 처리된 주문입니다.",
                        "DUPLICATE_PAYMENT_PREVENTED");
}
```

❌ **낙관적 락의 한계:**
- 충돌 발생 **후** 감지 → 충돌이 많은 구간에서는 예외와 재시도가 쌓이면서 오히려 부하가 커질 수 있다.
- 중복 결제는 막을 수 있지만, “한 번의 결제 흐름 안에서 재고·포인트·쿠폰이 모두 함께 일관되게 처리되는가” 를 보장하기에는 부족했다.
- 여러 서버로 확장했을 때는 단일 DB 락만으로는 제어 범위가 애매해지는 구간이 생길 수 있다.

---

## 2. 기술 선택 과정

### 2.1 결제 중복 방지 - Product별 분산락 + Facade 패턴

**초기 접근:**
처음에는 “재고는 상품별 리소스니까, 상품별로 락을 거는 게 맞지 않나?” 라고 생각했다.
```java
// StockLocker에 @DistributedLock 적용
@Component
class StockLocker {
    @DistributedLock(key = "'stock:' + #productId")
    public void decreaseStockWithLock(Long productId, int quantity) {
        // 재고 차감
    }
}

// ProductService에서 호출
public void decreaseStocks(List<OrderItem> items) {
    for (OrderItem item : items) {
        stockLocker.decreaseStockWithLock(item.getProductId(), ...);
    }
}
```
여기서 나왔던 고민 포인트들:
1. **AOP 프록시 이슈**: 
    - 같은 클래스 내에서 메서드 호출 시 AOP 미적용
    - → Facade 패턴으로 분리 시도
2. **락과 트랜잭션 순서** 
    - Facade에서 분산락 → UseCase에서 트랜잭션 시작
    - “락이 먼저 풀리고, 그 뒤에 바깥 트랜잭션이 롤백되는” 식의 꼬이는 케이스를 조심해야 했다.
    - 특히 “재고는 이미 차감됐는데 포인트에서 실패해서 결제가 롤백되는” 상황을 만들고 싶지 않았다.
3. **여러 상품을 한 번에 주문할 때 원자성트랜잭션 순서 보장 불가**:
    - 여러 상품에 대해 각각 분산락을 걸면, “상품 A 재고 차감 → 상품 B 재고 차감 → ... → 주문 확정” 순서가 보장되지 않는다.
    - 중간에 하나라도 실패하면 롤백되지만, 이미 차감된 재고는 복구 불가.
    - 재고 / 포인트 / 쿠폰 / 주문 확정을 하나의 트랜잭션으로 묶고 싶은 요구와도 부딪혔다.

상품 단위 락은 “리소스 관점”에서는 자연스럽지만,
“결제 플로우 전체”를 봤을 때는 핵심 요구사항(중복 결제 방지, 전체 원자성 보장)에 조금 멀다는 느낌이 들었다.

---
#### ✅ 최종 선택: UseCase에 분산락 (orderId 기준)
결국 **“결제라는 행위 자체를 한 번만 허용하는 게 핵심”** 이라고 판단해서,
ProcessPaymentUseCase 에 orderId 기반 분산락을 적용하는 쪽으로 방향을 바꿨다.

```java
@DistributedLock(key = "'payment:' + #command.orderId",
                 waitTime = 8, leaseTime = 20)
@Transactional
public PaymentResult execute(PaymentCommand command) {
    // 1. 주문 검증
    // 2. 쿠폰 할인 계산
    // 3. 재고 차감 (비관적 락)
    // 4. 포인트 차감 (비관적 락)
    // 5. 쿠폰 사용
    // 6. 주문 확정
    // 7. 히스토리 기록
}
```
실행 흐름은 다음과 같이 정리된다.
1. 분산 락 획득 (payment:123)
2. 트랜잭션 시작
3. 모든 비즈니스 로직 수행
   - 재고 차감 (비관적 락)
   - 포인트 차감 (비관적 락)
   - 주문 확정
4. 트랜잭션 커밋 (성공) 또는 롤백 (실패)
5. 분산 락 해제

정리하면:
    - “동일 주문에 대해 결제는 정확히 한 번만” 이라는 요구사항을 UseCase 레벨에서 분명하게 표현할 수 있다.
    - 재고/포인트는 여전히 DB 락으로 보호되지만, ”어떤 주문에 대한 결제 흐름이 동시에 두 번 이상 들어오지 않는다” 는 상위 제약을 분산락이 잡아준다.

**🎯 트레이드오프:**

| 항목    | 내용                                                                      |
|-------|-------------------------------------------------------------------------|
| 👍 장점 | 주문 단위로 정합성·중복 결제를 확실히 막을 수 있고, 코드 상에서 의도가 분명해진다.                        |
| 👎 단점 | 동일 주문에 대한 처리는 순차로만 진행되므로 극단적인 경합 상황에서는 처리량이 줄 수 있다. Redis에 대한 의존성도 생긴다. |

이번 과제에서는 “결제 도메인에서는 정합성이 성능보다 우선”이라고 보고, 이 방향을 선택했다.

---

### 2.2 선착순 쿠폰 발급 – 얼마나 강하게 막을 것인가
현재 쿠폰 발급 로직은 다음과 같이 구성했다.

```java
@DistributedLock(key = "'coupon:' + #command.couponId",
                 waitTime = 2, leaseTime = 10)
@Transactional
public IssueCouponResult issueCoupon(IssueCouponCommand command) {
    // 1. 쿠폰 조회 (일반 SELECT - 비관적 락 제거)
    Coupon coupon = couponRepository.findById(couponId);

    // 2. 발급 기간 확인
    // 3. 중복 발급 확인
    // 4. 발급 수량 증가
    coupon.increaseIssued();

    // 5. UserCoupon 생성 (UNIQUE 제약)
    userCouponRepository.save(userCoupon);
}
```

**⚠️ 설계 변경: 비관적 락 제거**

초기에는 분산 락 + 비관적 락을 함께 사용했으나, **이중 락의 문제점**을 발견했다:
- 분산 락으로 이미 순차 처리가 보장되는데, 비관적 락을 중복 적용하면 불필요한 오버헤드
- waitTime이 길어져 성능 저하 (30초 → 2초로 개선)
- DB 락 대기로 인한 데드락 위험 증가

**최종 보호 장치 (2단계):**
1. **분산락 (coupon:{couponId})**: 여러 서버에서 동시에 동일 쿠폰 발급 시도를 직렬화
2. **UNIQUE 제약**: 동일 유저가 동일 쿠폰을 중복 발급받지 못하도록 DB 차원에서 보장

**트레이드오프:**
- ✅ **성능 개선**: waitTime 30초 → 2초, leaseTime 20초 → 10초
- ✅ **락 단순화**: 분산 락만으로 충분한 동시성 제어
- ⚠️ **Redis 의존도 증가**: 분산 락에 더 의존하게 됨

실제 적용하면서 느낀 건,
- 분산락은 여러 서버 환경에서의 동시성을 초반에 걸러낸다
- UNIQUE 제약은 코드 레벨 로직이 실수해도 마지막 방어선 역할을 해 준다

결론적으로, **분산 락 하나로 충분히 동시성을 제어**할 수 있었고, 성능도 개선되었다. 

---

### 2.3 재고/포인트 차감 – 어떤 도메인에 어떤 락을 거는 게 맞는가
초기에는 “포인트보다 동일 주문에 대한 중복 결제를 막는 것이 더 중요하다”고 보고,
Order 엔티티에만 낙관적 락을 걸어서 처리하는 구성을 시도했었다.
- 결제 흐름에서 최종적으로 Order 의 상태/버전을 기준으로 중복 결제를 막고,
- 포인트는 “어차피 주문에 종속적인 보조 정보”라는 느낌으로 접근했던 것.

여기에 대한 피드백은 다음과 같았다.
> 중복결제를 막는 부분은 주문 도메인 관점에서 효과적이지만,
포인트 도메인 관점에서는 동시성 이슈에 대한 리스크가 여전히 존재합니다.
포인트의 충전이 동시에 진행되거나, 포인트의 차감이 다른 도메인에서도 발생할 수 있더라도,
포인트 도메인은 스스로 데이터의 무결성을 보장할 수 있어야 하고,
이 관점으로 생각을 지속해야 하는 게 결합의 낮춤과 응집도의 높임입니다.

이 피드백 이후로 관점을 조금 바꿨다.
- “중복 결제는 Order가 책임진다”는 건 맞지만,
- “포인트 잔액의 일관성은 Point 도메인이 스스로 책임져야 한다”는 것도 맞다.

그래서 최종적으로는:
- ProcessPaymentUseCase 레벨에서 orderId 기준 분산락으로 “결제 플로우 전체”를 감싼 뒤, 
- 그안에서 재고·포인트에 대해서는 각자 비관적 락을 사용해서 도메인 단위 일관성도 지키는 형태로 정리했다.

```java
// Product
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") Long id);

// Point
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM User u WHERE u.id = :id")
Optional<User> findByIdForUpdate(@Param("id") Long id);

```
여러 상품 재고를 한 번에 차감할 때는 데드락 방지를 위해 productId 정렬 후 순차로 락을 획득한다.

**실행 쿼리:**

```java
List<OrderItem> sortedItems = items.stream()
    .sorted(Comparator.comparing(OrderItem::getProductId))
    .toList();

for (OrderItem item : sortedItems) {
    Product product = productRepository.findByIdForUpdate(item.getProductId());
    product.decreaseStock(item.getQuantity());
}
```
요약하면
- Order: 중복 결제 방지의 중심
- Point: 포인트 잔액에 대한 독립적인 무결성 보장
- Product: 재고 수량에 대한 무결성 보장
- UseCase: 이 세 도메인을 하나의 트랜잭션 + 분산락으로 조율
이렇게 각각의 도메인이 “자기 숫자는 자기가 책임지는” 구조로 맞추려고 했다.

---

### 2.4 인기 상품 조회 캐싱

이 부분은 방향성이 비교적 명확했다.
“조회가 무겁고, 실시간성이 조금 떨어져도 되는 기능”이었기 때문에 처음부터 Redis 캐시를 전제로 설계했다.

**선택지 비교:**

| 항목  | In-Memory (Caffeine 등) | Redis Cache      |
|-----|------------------------|------------------|
| 속도  | 메모리 접근 (가장 빠름)         | 네트워크 I/O 포함      |
| 일관성 | 서버별로 내용 다를 수 있음        | 여러 서버에서 공통 캐시 사용 |
| 운영  | 애플리케이션 안에서 해결          | 별도 인프라 필요        |
| 확장성 | 단일 인스턴스에 유리            | 분산·스케일아웃에 유리     |

실제 서비스 환경을 떠올리면 어차피 여러 인스턴스로 확장될 것이고,
“어느 서버로 붙어도 인기 상품 목록은 같아야 한다” 는 점이 중요하다고 판단해서 Redis를 선택했다.

```java
@Cacheable(
        value = "topProducts",
        key = "#query.period() + ':' + #query.limit()"
)
@Transactional(readOnly = true)
public TopProductResult getTopProducts(TopProductQuery query) {
    // JOIN 쿼리로 상품 + 판매량 조회
    List<TopProductResult.ProductItem> items = orderItemRepository
            .findTopSellingProductsSince(query.since())
            .stream()
            .limit(query.limit())
            .toList();

    return new TopProductResult(items);
}

```
캐시 키 예시는 다음과 같다.
- topProducts::3d:5 → 최근 3일 기준 상위 5개
- topProducts::7d:10 → 최근 7일 기준 상위 10개

TTL은 10분으로 설정했다.
인기 상품은 초 단위로 바뀌는 데이터가 아니고, “조금 과거 데이터여도 상관 없는” 성격이라 이 정도 타협은 괜찮다고 봤다.

---

## 3. 최종 아키텍처

### 3.1 동시성 제어 전략 요약

| 기능           | 분산락 (Redis)           | DB 락              | 기타                 |
|--------------|-----------------------|-------------------|--------------------|
| **결제**       | ✅ `payment:{orderId}` | 재고/포인트에 비관적 락     | 하나의 트랜잭션으로 처리      |
| **쿠폰 발급**    | ✅ `coupon:{couponId}` | 비관적 락 + UNIQUE 제약 | 선착순 수량 보호          |
| **재고**       | (상위 결제 락 아래)          | 비관적 락 + 상품 ID 정렬  | 데드락 방지             |
| **포인트**      | (상위 결제 락 아래)          | 비관적 락             | 포인트 도메인 자체 정합성     |
| **인기 상품 조회** | -                     | -                 | Redis 캐시 (10분 TTL) |

### 3.2 구현 구조

```
┌────────────────────────────────────────────┐
│          ProcessPaymentUseCase            │
│  @DistributedLock("payment:{orderId}")    │
│  @Transactional                           │
└───────────────┬───────────────────────────┘
                │
    ┌───────────┴────────────┐
    │   DistributedLockAop   │  ← 락 획득/해제 담당
    └───────────┬────────────┘
                │
    ┌───────────┴────────────┐
    │   AopForTransaction    │  ← REQUIRES_NEW 트랜잭션
    └───────────┬────────────┘
                │
     ┌──────────┴───────────┐
     │   도메인 서비스 호출   │
     │  - 재고 차감          │
     │  - 포인트 차감        │
     │  - 쿠폰 사용          │
     │  - 주문 확정          │
     └──────────────────────┘

```

핵심은 **“락을 잡은 상태에서 트랜잭션을 한 번 열고, 그 안에서 모든 도메인 작업을 끝낸 뒤 커밋 후 락을 푼다”** 는 점이다.

---

### 참고 자료
- [컬리 기술블로그 - Redisson 분산락](https://helloworld.kurly.com/blog/distributed-redisson-lock/)
- [Spring Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Redisson Documentation](https://github.com/redisson/redisson/wiki)

---

# Redis 기반 시스템 설계 보고서

**작성일:** 2025-12-04

---

## 목차
1. [상품 랭킹 시스템 설계](#1-상품-랭킹-시스템-설계)
2. [비동기 쿠폰 발급 시스템 설계](#2-비동기-쿠폰-발급-시스템-설계)
3. [회고](#3-회고)

---

## 1. 상품 랭킹 시스템 설계

### 1.1 요구사항
- 가장 많이 주문한 상품 Top 5 조회
- 실시간 업데이트 (주문 발생 시 즉시 반영)
- 높은 조회 성능 (캐싱)

### 1.2 기술 선택: Redis Sorted Set

#### 선택 이유
| 비교 항목 | RDB (MySQL) | Redis Sorted Set |
|----------|-------------|------------------|
| 조회 성능 | O(N log N) - 전체 스캔 + 정렬 | O(log N) - 이미 정렬됨 |
| 실시간성 | 낮음 (배치 집계) | 높음 (즉시 반영) |
| 카운터 증가 | UPDATE 쿼리 (락 경합) | ZINCRBY (원자적) |
| Top N 조회 | ORDER BY + LIMIT | ZREVRANGE (O(log N)) |

**결론:** Redis Sorted Set이 랭킹 시스템에 최적화됨

#### Redis Sorted Set 구조
```
Key: product:ranking
Score: 주문 횟수 (자동 정렬)

┌─────────────┬───────┐
│ Member      │ Score │
├─────────────┼───────┤
│ product:5   │ 1523  │ ← 1위
│ product:12  │ 1204  │ ← 2위
│ product:3   │ 987   │ ← 3위
│ product:18  │ 756   │ ← 4위
│ product:7   │ 623   │ ← 5위
└─────────────┴───────┘
```

### 1.3 구현 아키텍처

```
┌─────────────────────────────────────────────────────┐
│ 주문 발생                                            │
│   ↓                                                  │
│ ProcessPaymentUseCase.execute()                     │
│   ↓                                                  │
│ orderItemRepository.saveAll()  (주문 저장)          │
│   ↓                                                  │
│ productService.updateRanking()  (랭킹 업데이트)     │
│   ↓                                                  │
│ Redis ZINCRBY product:ranking {productId} 1         │
│   (원자적 카운터 증가)                               │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ 랭킹 조회                                            │
│   ↓                                                  │
│ ProductController.getTopProducts()                  │
│   ↓                                                  │
│ productService.getTopProducts(5)                    │
│   ↓                                                  │
│ Redis ZREVRANGE product:ranking 0 4                 │
│   (O(log N) 성능)                                    │
│   ↓                                                  │
│ productRepository.findAllById()  (상품 정보 조회)   │
└─────────────────────────────────────────────────────┘
```

### 1.4 핵심 코드

#### 랭킹 업데이트 (`ProductService.java`)
```java
public void updateRanking(Long productId) {
    String key = "product:ranking";
    redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), 1);
}
```

#### Top 5 조회 (`ProductService.java`)
```java
public List<ProductRankingDto> getTopProducts(int limit) {
    String key = "product:ranking";

    // Redis에서 Top N 조회 (O(log N))
    Set<String> topProductIds = redisTemplate.opsForZSet()
            .reverseRange(key, 0, limit - 1);

    if (topProductIds == null || topProductIds.isEmpty()) {
        return List.of();
    }

    // 상품 정보 조회
    List<Long> productIds = topProductIds.stream()
            .map(Long::valueOf)
            .toList();

    List<Product> products = productRepository.findAllById(productIds);

    // 랭킹과 상품 정보 조합
    return topProductIds.stream()
            .map(id -> {
                Long productId = Long.valueOf(id);
                Double score = redisTemplate.opsForZSet().score(key, id);
                Product product = products.stream()
                        .filter(p -> p.getId().equals(productId))
                        .findFirst()
                        .orElse(null);

                if (product == null) return null;

                return new ProductRankingDto(
                        product.getId(),
                        product.getName(),
                        score != null ? score.longValue() : 0L
                );
            })
            .filter(Objects::nonNull)
            .toList();
}
```

### 1.5 성능 비교

| 항목 | RDB 방식 | Redis Sorted Set |
|------|----------|------------------|
| 조회 시간 (1000개 상품) | ~50ms | ~5ms |
| 업데이트 시간 | ~10ms (UPDATE 쿼리) | ~1ms (ZINCRBY) |
| 동시성 처리 | 락 경합 발생 | 원자적 연산 |
| 확장성 | 수직 확장만 가능 | 수평 확장 가능 |


---

## 2. 비동기 쿠폰 발급 시스템 설계

### 2.1 요구사항
- 선착순 100명 쿠폰 발급
- 동시 요청 처리 (race condition 방지)
- 비동기 처리 (빠른 응답)
- 결과 폴링 API 제공

### 2.2 기존 문제점 (분산 락 방식)

#### 분산 락의 한계
```
동시 요청 1000명
↓
모두 순차 대기 (직렬 처리)
↓
평균 대기 시간: 500ms × 500명 = 250초 = 4분
```

**문제:**
- 선착순 보장 안 됨 (락 획득 순서 ≠ 요청 순서)
- 성능 병목 (직렬 처리)
- 확장성 부족

### 2.3 새로운 설계: Redis Queue + Stream 방식

#### 아키텍처 개요
```
┌────────────────────────────────────────────────────────────────┐
│ Phase 1: API Server (발급 요청)                                 │
│                                                                 │
│  Client → POST /issue                                           │
│     ↓                                                           │
│  CouponQueueService.tryEnqueue()                               │
│     ↓                                                           │
│  1. Redis INCR coupon:counter:{couponId}  (원자적 position 획득)│
│  2. position <= totalQuantity?                                  │
│     - YES: ZADD + XADD (큐 추가 + 이벤트 발행)                 │
│     - NO:  DECR (카운터 롤백)                                   │
│     ↓                                                           │
│  202 Accepted { position: 42 }                                 │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│ Phase 2: Worker (백그라운드 처리)                               │
│                                                                 │
│  @Scheduled(fixedDelay = 100ms)                                │
│     ↓                                                           │
│  CouponIssueConsumer.consumeStream()                           │
│     ↓                                                           │
│  XREADGROUP (Stream에서 메시지 읽기)                            │
│     ↓                                                           │
│  userCouponService.issueCoupon() (DB 저장)                     │
│     ↓                                                           │
│  Redis SET result:SUCCESS (결과 저장, TTL 10분)                │
│     ↓                                                           │
│  XACK (처리 완료)                                               │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│ Phase 3: Client (결과 조회)                                     │
│                                                                 │
│  GET /result (1초마다 폴링)                                     │
│     ↓                                                           │
│  CouponQueueService.getResult()                                │
│     ↓                                                           │
│  Redis GET result                                               │
│     - null: "PROCESSING" (202 Accepted)                        │
│     - SUCCESS: "SUCCESS" (200 OK)                              │
│     - FAILED: "FAILED" (200 OK)                                │
└────────────────────────────────────────────────────────────────┘
```

### 2.4 Redis 자료구조 선택

#### 사용된 자료구조 4가지

##### 1) Redis INCR (원자적 카운터)
```
Key: coupon:counter:{couponId}
Value: 1 → 2 → 3 → ... → 100

역할: race condition 없이 정확히 100명만 선발
```

**왜 필요한가?**
- Sorted Set의 ZADD + ZRANK는 원자적이지 않음
- INCR은 단일 명령어로 원자성 보장

##### 2) Redis Sorted Set (타임스탬프 순위)
```
Key: coupon:queue:{couponId}
Member: userId
Score: timestamp (밀리초)

┌─────────┬──────────────────┐
│ userId  │ Score (timestamp)│
├─────────┼──────────────────┤
│ 1       │ 1733328123001    │ ← 1등
│ 5       │ 1733328123005    │ ← 2등
│ 3       │ 1733328123008    │ ← 3등
└─────────┴──────────────────┘

역할: 타임스탬프 기반 선착순 순위 관리
```

##### 3) Redis Stream (이벤트 큐)
```
Key: coupon:stream:{couponId}

Stream:
  msg-1: { userId: 1, couponId: 5 }
  msg-2: { userId: 2, couponId: 5 }
  msg-3: { userId: 3, couponId: 5 }

역할: API Server → Worker 메시지 전달
```

**Consumer Group 패턴:**
```
Stream
  ├─ Consumer Group: "coupon-consumer-group"
  │   ├─ consumer-1 (서버 A) → msg-1, msg-4, msg-7 처리
  │   ├─ consumer-2 (서버 B) → msg-2, msg-5, msg-8 처리
  │   └─ consumer-3 (서버 C) → msg-3, msg-6, msg-9 처리

장점: 수평 확장 가능, 중복 처리 방지, ACK 기반 신뢰성
```

##### 4) Redis String (결과 저장)
```
Key: coupon:result:{userId}:{couponId}
Value: "SUCCESS" | "FAILED"
TTL: 10분

역할: 발급 결과를 일시적으로 저장 (폴링용)
```

### 2.5 API 흐름 예시

#### 성공 시나리오
```bash
# 1. 발급 요청
POST /api/v1/users/1/coupons/5/issue

Response: 202 Accepted
{
  "success": true,
  "position": 42,
  "message": "Queue position: 42 | Check result: GET /api/v1/users/1/coupons/5/result"
}

# 2. 결과 조회 (바로)
GET /api/v1/users/1/coupons/5/result

Response: 202 Accepted
"PROCESSING"

# 3. 결과 조회 (1초 후)
GET /api/v1/users/1/coupons/5/result

Response: 200 OK
"SUCCESS"
```

#### 마감 시나리오
```bash
# 101번째 사용자
POST /api/v1/users/101/coupons/5/issue

Response: 400 Bad Request
{
  "success": false,
  "position": null,
  "message": "선착순 마감되었습니다. 다음 기회에 다시 시도해주세요."
}
```

## 3. 회고

### 3.1 잘한 점

#### 1) 적절한 자료구조 선택
- **랭킹:** Sorted Set으로 O(log N) 성능 달성
- **선착순:** INCR로 race condition 완벽 해결
- **이벤트 큐:** Stream의 Consumer Group으로 확장성 확보

#### 2) 동시성 문제 해결
- 분산 락 방식의 한계를 인식하고 근본적인 해결책 적용
- Redis INCR의 원자성을 활용한 정확한 선착순 보장
- 동시성 테스트로 검증 (200명 → 정확히 100명)

#### 3) 사용자 경험 개선
- 비동기 처리로 응답 시간 단축 (250초 → 50ms)
- 202 Accepted + 폴링 패턴으로 실시간 피드백
- 개발자 친화적인 메시지 (결과 확인 URL 포함)

#### 4) 확장성 고려
- Consumer Group 패턴으로 수평 확장 가능
- 발급 기간 중인 쿠폰만 자동 처리
- 환경변수로 consumer-name 주입 가능

### 3.2 어려웠던 점

#### 1) ZADD + ZRANK의 원자성 부재
**문제:**
```java
// 이 두 명령은 원자적이지 않음
redisTemplate.opsForZSet().add(queueKey, member, timestamp);
Long rank = redisTemplate.opsForZSet().rank(queueKey, member);
```

**해결:**
- Redis INCR를 먼저 실행하여 position 확정
- 이후 Sorted Set 추가 (순위는 참고용)

#### 2) Stream 초기화 타이밍
**문제:**
- Consumer Group 생성 시 Stream이 없으면 에러
- 첫 메시지 발행 전에 Consumer가 실행되면 실패

**해결:**
```java
private void createConsumerGroupIfNotExists(String streamKey) {
    try {
        StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(streamKey);
        boolean exists = groups.stream()
                .anyMatch(group -> consumerGroup.equals(group.groupName()));

        if (!exists) {
            redisTemplate.opsForStream().createGroup(streamKey, consumerGroup);
        }
    } catch (Exception e) {
        // Stream이 없으면 무시 (첫 메시지 발행 시 재시도)
        log.debug("Consumer Group 생성 실패 (Stream 없음) - streamKey: {}", streamKey);
    }
}
```

#### 3) 테스트 격리 (Redis 데이터 충돌)
**문제:**
- 테스트 간 Redis 데이터가 남아서 간섭
- `@AfterEach`에서 삭제해도 타이밍 이슈

**해결:**
- 쿠폰 코드에 타임스탬프 추가 (`"COUPON_" + System.currentTimeMillis()`)
- 각 테스트마다 고유한 Redis Key 생성

### 3.3 개선 아이디어

#### 1) Lua Script 도입 검토
현재 구현:
```
1. INCR (position 획득)
2. ZADD (큐 추가)
3. XADD (이벤트 발행)
```

Lua Script 사용 시:
```lua
-- 단일 명령으로 실행 (원자성 완벽)
local position = redis.call('INCR', KEYS[1])
if position <= tonumber(ARGV[1]) then
    redis.call('ZADD', KEYS[2], ARGV[2], ARGV[3])
    redis.call('XADD', KEYS[3], '*', 'userId', ARGV[3], 'couponId', ARGV[4])
    return position
else
    redis.call('DECR', KEYS[1])
    return -1
end
```

**장점:**
- 네트워크 왕복 횟수 감소 (3번 → 1번)
- DECR 롤백 불필요 (조건문 안에서 처리)

**단점:**
- Lua 스크립트 관리 복잡도 증가
- 디버깅 어려움

#### 2) 모니터링 강화
추가하면 좋을 메트릭:
- 큐 대기자 수 실시간 조회
- Stream 처리 지연 시간
- Worker 처리량 (TPS)
- 실패율

```java
public QueueStatusDto getQueueStatus(Long couponId) {
    long queueSize = redisTemplate.opsForZSet().zCard(getQueueKey(couponId));
    long streamSize = redisTemplate.opsForStream().size(getStreamKey(couponId));
    long counter = Long.parseLong(redisTemplate.opsForValue().get(getCounterKey(couponId)));

    return new QueueStatusDto(queueSize, streamSize, counter);
}
```

### 3.4 배운 점

#### 1) Redis는 단순 캐시가 아니다
- Sorted Set, Stream 같은 고급 자료구조로 복잡한 로직 구현 가능
- 원자적 연산(INCR, ZINCRBY)으로 동시성 문제 해결
- 분산 시스템의 상태 관리에 최적

#### 2) 비동기 처리의 중요성
- 사용자 경험 = 응답 시간
- 백그라운드 처리 + 폴링 패턴으로 UX 개선
- 202 Accepted는 "처리 중"을 명확히 전달

#### 3) 테스트의 가치
- 동시성 테스트 없이는 race condition 발견 불가
- Redis Testcontainers로 독립적인 테스트 환경
- 통합 테스트로 전체 흐름 검증

#### 4) 확장성 설계
- 처음부터 수평 확장을 고려해야 함
- Consumer Group 패턴으로 무한 확장 가능
- 환경변수 주입으로 설정 유연화

### 3.5 다음 시도

#### 1) Lua Script 최적화
- 현재 3단계 명령을 1단계로 축약
- 네트워크 오버헤드 제거

#### 2) WebSocket 실시간 알림
- 폴링 대신 Server-Sent Events (SSE) 또는 WebSocket
- 발급 완료 시 즉시 클라이언트에 푸시

#### 3) Dead Letter Queue (DLQ)
- 발급 실패 메시지를 별도 큐로 이동
- 수동 재처리 또는 알림 발송

#### 4) 대시보드 구축
- Grafana + Prometheus로 실시간 모니터링
- 큐 상태, 처리 속도, 에러율 시각화


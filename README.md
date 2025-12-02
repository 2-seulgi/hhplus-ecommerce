# E-Commerce 서비스

항해플러스 백엔드 과제 - 이커머스 서비스 구현 (STEP 5/6)

## 📋 프로젝트 개요

이 프로젝트는 레이어드 아키텍처 기반의 이커머스 서비스입니다.
DB를 사용하지 않고 인메모리 저장소로 모든 데이터를 관리하며, 동시성 제어를 고려한 설계를 포함합니다.

### 주요 기능

- **포인트 관리**: 포인트 충전, 사용, 잔액 조회, 내역 조회
- **상품 관리**: 상품 목록 조회, 상세 조회, 재고 조회, 인기 상품 조회
- **장바구니**: 상품 추가, 조회, 삭제
- **주문/결제**: 주문 생성, 결제 처리, 환불, 취소, 내역 조회
- **쿠폰 시스템**: 쿠폰 발급, 사용, 보유 쿠폰 조회 (선착순 발급 지원)

## 🏗️ 아키텍처 설계

### 레이어드 아키텍처

이 프로젝트는 **도메인 중심 레이어드 아키텍처**를 채택했습니다.

```
src/main/java/com/hhplus/be/
├── {domain}/                    # 도메인별 패키지 (cart, product, order, point, user, coupon 등)
│   ├── controller/              # Presentation Layer - 요청/응답 처리
│   │   └── dto/                 # Controller용 DTO
│   ├── service/                 # Application Layer - 비즈니스 로직 조율
│   │   └── dto/                 # Service용 DTO (Query, Command, Result)
│   ├── domain/                  # Domain Layer - 핵심 비즈니스 규칙
│   │   ├── {Entity}.java        # 도메인 엔티티
│   │   └── {ValueObject}.java   # 값 객체
│   └── infrastructure/          # Infrastructure Layer - 데이터 저장소
│       ├── {Entity}Repository.java           # Repository 인터페이스
│       └── InMemory{Entity}Repository.java   # 인메모리 구현체
├── common/                      # 공통 모듈
│   ├── exception/               # 공통 예외 처리
│   └── response/                # 공통 응답 형식
└── config/                      # 설정 (Clock 등)
```

### 주요 설계 결정

#### 1. 도메인별 패키지 구조

**선택한 방식**: 도메인별로 패키지를 분리하고, 각 도메인 내에서 계층을 구분

**선택 이유**:
- 도메인 응집도 향상: 관련된 코드가 한 곳에 모여 있어 이해와 수정이 용이
- 확장성: 새로운 도메인 추가 시 기존 코드에 영향 최소화
- 팀 협업: 도메인별로 작업 분리가 명확

**트레이드오프**:
- 계층별 패키지 구조 대비 계층 간 의존성을 파악하기 어려울 수 있음
- 하지만 도메인 크기가 작을 때는 도메인별 구조가 더 직관적

#### 2. Repository 인터페이스와 인메모리 구현 분리

**선택한 방식**: Repository 인터페이스를 정의하고 `InMemory*Repository` 구현체 제공

```java
public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    List<Product> findAll();
}

@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final ConcurrentHashMap<Long, Product> store = new ConcurrentHashMap<>();
    // ...
}
```

**선택 이유**:
- 테스트 용이성: Service 테스트 시 Repository를 Mock으로 대체 가능
- 향후 확장: JPA 등 실제 DB 구현으로 전환 시 인터페이스만 유지하면 됨
- 의존성 역전: Service가 구체적인 저장소 구현에 의존하지 않음

**트레이드오프**:
- 초기 구현 복잡도 증가 (인터페이스 + 구현체)
- 하지만 유지보수성과 테스트 용이성 면에서 충분히 가치 있음

#### 3. 낙관적 락(@Version)을 활용한 동시성 제어 준비

**선택한 방식**: 재고 차감, 포인트 사용 등에 `@Version` 필드 추가

```java
@Entity
public class Product {
    @Version
    private Long version;

    public void decreaseStock(int quantity) {
        // 재고 차감 로직
        // version 필드로 낙관적 락 적용 (OptimisticLockException 발생 가능)
    }
}
```

**선택 이유**:
- 성능: 비관적 락 대비 읽기 성능이 우수
- 확장성: 실제 DB 도입 시 JPA의 @Version과 호환
- 충돌 감지: 동시 수정 시 예외 발생으로 데이터 정합성 보장

**트레이드오프**:
- 충돌 발생 시 재시도 로직 필요
- 충돌이 빈번한 경우 성능 저하 가능

#### 4. Clock 주입을 통한 시간 처리

**선택한 방식**: `java.time.Clock`을 DI로 주입받아 시간 처리

```java
@Service
public class OrderService {
    private final Clock clock;

    public PaymentResult processPayment(PaymentCommand command) {
        Instant now = Instant.now(clock);  // 테스트 가능한 시간
        // ...
    }
}
```

**선택 이유**:
- 테스트 용이성: 테스트에서 고정된 시간 주입 가능
- 시간 관련 버그 방지: `Instant.now()` 직접 호출 시 테스트 어려움
- 일관성: 모든 시간 처리를 Clock을 통해 수행

**트레이드오프**:
- 초기 설정 복잡도 증가
- 하지만 시간 의존적인 로직(만료, 유효기간) 테스트에 필수적

#### 5. DTO 분리 (Controller DTO vs Service DTO)

**선택한 방식**: Controller와 Service에서 사용하는 DTO를 분리

- **Controller DTO**: `*Request`, `*Response` - API 명세와 1:1 매칭
- **Service DTO**: `*Command`, `*Query`, `*Result` - 비즈니스 로직 표현

**선택 이유**:
- 계층 간 결합도 감소: API 변경이 Service 로직에 영향 없음
- 명확한 책임 분리: Controller는 변환만, Service는 비즈니스 로직만
- 테스트 독립성: Service 테스트가 HTTP 응답 형식에 의존하지 않음

**트레이드오프**:
- 코드량 증가 (DTO 클래스 2배)
- 변환 로직 필요 (from/to 메서드)
- 하지만 장기적으로 유지보수성 향상

#### 6. ConcurrentHashMap 사용

**선택한 방식**: 인메모리 저장소에 `ConcurrentHashMap` 사용

**선택 이유**:
- 스레드 안전성: 멀티스레드 환경에서 동시 접근 안전
- 성능: HashMap 대비 약간의 오버헤드로 동시성 지원
- 향후 확장: 동시성 테스트 시나리오 대응 가능

**트레이드오프**:
- 단일 스레드 환경에서는 HashMap보다 약간 느림
- 하지만 과제 요구사항(동시성 제어)을 고려하면 필수적

## 📊 테스트 커버리지

```bash
./gradlew test jacocoTestReport
```

| 항목 | 결과 |
|------|------|
| 전체 커버리지 | 53% |
| 단위 테스트 | 92개 통과 |
| 통합 테스트 | 0개 (Controller 레이어 미테스트) |

### 도메인별 커버리지

| 도메인 | 커버리지 | 주요 테스트 |
|--------|----------|------------|
| CartService | 91% | 장바구니 추가/조회/삭제 |
| PointService | 100% | 포인트 충전/사용/조회 |
| ProductService | 91% | 상품 조회/재고/인기상품 |
| CouponService | 93% | 쿠폰 발급/사용/조회 |
| OrderService | 72% | 주문/결제/환불/취소 |

### 커버리지가 70%에 미달하는 이유

- **Controller 레이어**: 0% (통합 테스트 미작성)
- **DTO 클래스**: 대부분 0% (단순 데이터 클래스)
- **Infrastructure**: 일부만 테스트 (InMemory 구현체)

**서비스 레이어는 평균 89% 이상**으로 충분한 테스트 커버리지를 확보했습니다.

## 🚀 실행 방법

### 1. 로컬 개발 환경 설정

#### Docker Compose로 MySQL + Redis 실행

```bash
# 시작
docker-compose up -d

# 상태 확인
docker-compose ps

# 로그 확인
docker-compose logs -f

# 종료
docker-compose down

# 볼륨까지 삭제 (데이터 초기화)
docker-compose down -v
```

### 2. 빌드 및 테스트

```bash
# 빌드
./gradlew build

# 테스트 실행 (Testcontainers 자동 시작)
./gradlew test

# 커버리지 리포트 생성
./gradlew jacocoTestReport

# 커버리지 리포트 확인
open build/reports/jacoco/test/html/index.html
```

**테스트 환경:**
- MySQL 8.0 컨테이너 자동 생성 (Testcontainers)
- Redis 7 컨테이너 자동 생성 (Testcontainers)
- 각 테스트마다 격리된 환경에서 실행

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

서버가 `http://localhost:8080`에서 실행됩니다.

**필수 조건:**
- Docker Compose로 MySQL + Redis 실행 중이어야 함
- `application.yml`에서 `localhost:3306`, `localhost:6379` 연결

## 📝 API 명세

### 포인트 API

- `GET /users/{userId}/point` - 포인트 잔액 조회
- `POST /users/{userId}/point/charge` - 포인트 충전
- `GET /users/{userId}/point/history` - 포인트 내역 조회

### 상품 API

- `GET /products` - 상품 목록 조회
- `GET /products/{productId}` - 상품 상세 조회
- `GET /products/{productId}/stock` - 상품 재고 조회
- `GET /products/top` - 인기 상품 조회

### 장바구니 API

- `GET /users/{userId}/cart` - 장바구니 조회
- `POST /users/{userId}/cart` - 장바구니 추가
- `DELETE /users/{userId}/cart/{productId}` - 장바구니 삭제

### 주문 API

- `POST /users/{userId}/orders` - 주문 생성
- `GET /users/{userId}/orders` - 주문 목록 조회
- `GET /users/{userId}/orders/{orderId}` - 주문 상세 조회
- `POST /users/{userId}/orders/{orderId}/payment` - 결제 처리
- `POST /users/{userId}/orders/{orderId}/refund` - 환불 처리
- `POST /users/{userId}/orders/{orderId}/cancel` - 주문 취소

### 쿠폰 API

- `POST /users/{userId}/coupons/{couponId}` - 쿠폰 발급
- `GET /users/{userId}/coupons` - 보유 쿠폰 조회

## 🔒 동시성 제어 (STEP 6)

### 현재 적용된 방식

1. **낙관적 락 (@Version)**
   - Product.decreaseStock() - 재고 차감
   - User.use() / User.charge() - 포인트 사용/충전
   - Coupon.incrementIssuedQuantity() - 쿠폰 발급 수량 증가

2. **ConcurrentHashMap**
   - 모든 인메모리 저장소에 적용
   - 멀티스레드 환경에서 안전한 CRUD 작업

### 향후 계획 (STEP 6 심화)

- 선착순 쿠폰 발급 동시성 테스트 추가
- 재고 차감 Race Condition 시나리오 테스트
- 낙관적 락 실패 시 재시도 로직 구현

## 🎯 체크리스트

### ✅ STEP 5 완료 항목

- [x] 도메인 모델 구현 (User, Product, Order, Coupon, Cart 등)
- [x] 유스케이스 구현 (포인트, 상품, 장바구니, 주문, 쿠폰)
- [x] 레이어드 아키텍처 (도메인별 Controller-Service-Domain-Infrastructure)
- [x] 재고 관리 (조회/차감/복구)
- [x] 주문/결제 (생성/결제/환불/취소)
- [x] 선착순 쿠폰 (발급/사용/만료 로직)
- [x] 단위 테스트 (92개, 서비스 레이어 89% 평균 커버리지)

### 🔥 STEP 6 진행 예정

- [ ] 동시성 제어 통합 테스트
- [ ] 선착순 쿠폰 Race Condition 테스트
- [ ] 낙관적 락 재시도 로직
- [ ] 인기 상품 집계 고도화

## 📚 기술 스택

- **Language**: Java 17
- **Framework**: Spring Boot 3.5.7
- **Database**: MySQL 8.0
- **Cache**: Redis 7
- **Distributed Lock**: Redisson 3.24.3
- **Build Tool**: Gradle
- **Test**: JUnit 5, Mockito, AssertJ, Testcontainers
- **Coverage**: JaCoCo
- **Infra**: Docker Compose

## 🤔 회고

### ✨ 잘한 점

- 도메인별 패키지 구조로 응집도 높은 설계
- Repository 인터페이스 분리로 테스트 용이성 확보
- Service 레이어 단위 테스트 충실 (평균 89% 커버리지)
- Clock 주입으로 시간 의존적 로직 테스트 가능
- DTO 분리로 계층 간 결합도 감소

### 😓 어려웠던 점

- 인메모리 저장소에서 낙관적 락 시뮬레이션
- 통합 테스트 없이 70% 커버리지 달성 어려움
- Controller DTO와 Service DTO 분리로 인한 코드량 증가

### 🚀 다음에 시도할 것

- Controller 통합 테스트 추가로 70% 이상 커버리지 달성
- 동시성 테스트 시나리오 작성 및 검증
- 실제 DB(JPA) 전환 시 Repository 인터페이스 유지 검증
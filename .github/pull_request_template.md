## :pushpin: PR 제목 규칙
[STEP05 또는 STEP06] 이름

---
## ⚠️ **중요: 이번 과제는 DB를 사용하지 않습니다**
> 모든 데이터는 **인메모리(Map, Array, Set 등)**로 관리해야 합니다.  
> 실제 DB 연동은 다음 챕터(데이터베이스 설계)에서 진행합니다.

---
## 📋 **과제 체크리스트**

### ✅ **STEP 5: 레이어드 아키텍처 기본 구현** (필수)
- [ ] **도메인 모델 구현**: Entity, Value Object가 정의되었는가?
- [ ] **유스케이스 구현**: API 명세가 유스케이스로 구현되었는가?
- [ ] **레이어드 아키텍처**: 4계층(Presentation, Application, Domain, Infrastructure)으로 분리되었는가?
- [ ] **재고 관리**: 재고 조회/차감/복구 로직이 구현되었는가?
- [ ] **주문/결제**: 주문 생성 및 결제 프로세스가 구현되었는가?
- [ ] **선착순 쿠폰**: 쿠폰 발급/사용/만료 로직이 구현되었는가?
- [ ] **단위 테스트**: 테스트 커버리지 70% 이상 달성했는가?

### 🔥 **STEP 6: 동시성 제어 및 고급 기능** (심화)
- [ ] **동시성 제어**: 선착순 쿠폰 발급의 Race Condition이 방지되었는가?
- [ ] **통합 테스트**: 동시성 시나리오를 검증하는 테스트가 작성되었는가?
- [ ] **인기 상품 집계**: 조회수/판매량 기반 순위 계산이 구현되었는가?
- [ ] **문서화**: README.md에 동시성 제어 분석이 작성되었는가?

### 🏗️ **아키텍처 설계**
- [ ] **의존성 방향**: Domain ← Application ← Infrastructure 방향이 지켜졌는가?
- [ ] **책임 분리**: 각 계층의 책임이 명확히 분리되었는가?
- [ ] **테스트 가능성**: Mock/Stub을 활용한 테스트가 가능한 구조인가?
- [ ] **인메모리 저장소**: DB 없이 모든 데이터가 인메모리로 관리되는가?
- [ ] **Repository 패턴**: 인터페이스와 인메모리 구현체가 분리되었는가?

---
## 🔗 **주요 구현 커밋**

### 기반 구조
- Clock 설정 (시간 처리 테스트 가능): `b2398c7`
- JaCoCo 커버리지 플러그인: `853828c`

### 도메인 & 인프라
- Coupon 인프라 (Repository): `81fc58b`
- UserCoupon 전체 구현: `1d4d87e`
- OrderDiscount 인프라: `6dce9e8`

### UseCase & API
- 포인트 API (충전/내역/잔액): 기존 구현
- 상품 API (목록/상세/재고): 기존 구현
- 인기 상품 조회 API: `4737258`
- 장바구니 API (추가/조회/삭제): `a60e20e`
- 주문 생성 API: `e0329fa`
- **주문/결제 API** (결제/환불/취소/목록/상세): `b7deb7e`
- **쿠폰 API** (발급/조회): `1d4d87e`

### 비즈니스 로직
- **OrderService** (결제/환불/취소/조회): `38434e4`
  - 쿠폰 적용 로직
  - 재고 차감 및 복구
  - 포인트 차감 및 환불
  - 낙관적 락 적용 준비

### 테스트
- OrderServicePaymentTest (6개): `c2a4e7b`
- OrderServiceRefundCancelTest (9개): `c2a4e7b`
- CouponServiceTest (8개): `c2a4e7b`
- **총 92개 테스트 통과**, 서비스 레이어 평균 89% 커버리지

### 리팩토링 & 문서화
- Controller/Service DTO 분리: `d8ad72b`
- README 작성 (아키텍처 설계 분석): `797cf69`

### STEP 6 (동시성 제어 준비 완료)
- 낙관적 락 (@Version) 적용: Product, User, Coupon
- ConcurrentHashMap 사용: 모든 InMemory Repository
- 인기 상품 집계 구현 완료: `4737258`

---
## 💬 **리뷰 요청 사항**

### 질문/고민 포인트
1.
2.

### 특별히 리뷰받고 싶은 부분
- 

---
## 📊 **테스트 및 품질**

| 항목 | 결과 |
|------|------|
| 테스트 커버리지 | X% |
| 단위 테스트 | X개 |
| 통합 테스트 | X개 |
| 동시성 테스트 | 통과/실패 |

---
## 🔒 **동시성 제어 방식** (STEP 6 필수)

**선택한 방식:**
- [ ] Mutex/Lock
- [ ] Semaphore
- [ ] Atomic Operations
- [ ] Queue 기반
- [ ] 기타: ___________

**구현 이유:**
- 

**참고 문서:**
- README.md의 동시성 제어 분석 섹션 참조

---
## 🎯 **아키텍처 설계**

### 디렉토리 구조
```
src/
├── presentation/     # Controller, Handler
├── application/      # UseCase, Service
├── domain/          # Entity, Value Object, Domain Service
└── infrastructure/  # Repository Interface + 인메모리 구현체
    └── memory/      # InMemoryXxxRepository
```

### 주요 설계 결정
- **선택한 아키텍처**: 레이어드 아키텍처
- **데이터 저장 방식**: 인메모리 (Map, Array, Set)
- **선택 이유**:
- **트레이드오프**:

---
## 📝 **회고**

### ✨ 잘한 점
- 

### 😓 어려웠던 점
- 

### 🚀 다음에 시도할 것
- 

---
## 📚 **참고 자료**
<!-- 학습에 도움이 된 자료가 있다면 공유해주세요 -->
- 

---
## ✋ **체크리스트 (제출 전 확인)**

- [ ] DB 관련 라이브러리를 사용하지 않았는가? (TypeORM, Prisma, Sequelize 등)
- [ ] 모든 Repository가 인메모리로 구현되었는가?
- [ ] package.json에 DB 드라이버가 없는가? (pg, mysql2, mongodb 등)
- [ ] 환경변수에 DB 연결 정보가 없는가?
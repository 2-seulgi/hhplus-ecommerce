# E-commerce Mock API Server

API 계약 확인을 위한 간단한 Mock 서버입니다.

## 목적

- ✅ API 엔드포인트 확인
- ✅ 요청/응답 구조 검증
- ✅ 기본적인 CRUD 테스트
- ❌ 복잡한 비즈니스 로직 구현 (실제 서버에서 구현)

## 설치 및 실행

### 1. 의존성 설치
```bash
cd mock-server
npm install
```

### 2. 서버 실행
```bash
# 미들웨어 포함 실행 (권장)
npm start

# 또는 단순 실행
npm run start:simple
```

서버 실행 후: `http://localhost:8080`

## API 엔드포인트

### 포인트 API
- `GET /api/v1/users/:userId/points/balance` - 포인트 잔액 조회
- `POST /api/v1/users/:userId/points/charge` - 포인트 충전
- `GET /api/v1/users/:userId/points/history` - 포인트 내역 조회

### 상품 API
- `GET /api/v1/products` - 상품 목록 조회
- `GET /api/v1/products/:id` - 상품 상세 조회
- `GET /api/v1/products/:id/stock` - 상품 재고 조회
- `GET /api/v1/products/top` - 인기 상품 조회

### 장바구니 API
- `GET /api/v1/users/:userId/cart/items` - 장바구니 조회
- `POST /api/v1/users/:userId/cart/items` - 장바구니 담기
- `DELETE /api/v1/users/:userId/cart/items/:cartItemId` - 장바구니 삭제
- `PATCH /api/v1/users/:userId/cart/items/:cartItemId` - 장바구니 수량 변경

### 주문 API
- `GET /api/v1/users/:userId/orders` - 주문 내역 조회
- `GET /api/v1/users/:userId/orders/:orderId` - 주문 상세 조회
- `POST /api/v1/users/:userId/orders` - 주문 생성

### 결제 API
- `POST /api/v1/users/:userId/orders/:orderId/payment` - 주문 결제
- `POST /api/v1/users/:userId/orders/:orderId/refund` - 주문 환불

### 쿠폰 API
- `POST /api/v1/users/:userId/coupons/:couponId/issue` - 쿠폰 발급
- `GET /api/v1/users/:userId/coupons` - 보유 쿠폰 조회

## 테스트 데이터

### 사용자
- User 1: `userId=1`, balance=50,000원
- User 2: `userId=2`, balance=100,000원

### 상품
- Product 1: 무선 마우스 (29,900원, 재고 100개)
- Product 2: 기계식 키보드 (89,000원, 재고 50개)
- Product 3: USB 허브 (15,000원, **재고 0개** - 주문 생성 시 에러 발생)

### 쿠폰
- Coupon 1: `WELCOME10` (10% 할인, 발급 가능)
- Coupon 2: `SUMMER5000` (5,000원 할인, **소진됨** - 발급 시 에러 발생)

### 주문
- Order 1: 확정된 주문 (CONFIRMED)
- Order 2: 대기 중인 주문 (PENDING)

## 에러 응답 예시

### 400 Bad Request - 잘못된 충전 금액
```bash
curl -X POST http://localhost:8080/api/v1/users/1/points/charge \
  -H "Content-Type: application/json" \
  -d '{"amount": 500}'
```
Response:
```json
{
  "timestamp": "2025-10-29T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "INVALID_AMOUNT",
  "message": "충전 금액은 1,000원 ~ 1,000,000원 사이여야 합니다",
  "path": "/api/v1/users/1/points/charge"
}
```

### 404 Not Found - 존재하지 않는 사용자
```bash
curl http://localhost:8080/api/v1/users/999/points/balance
```
Response:
```json
{
  "timestamp": "2025-10-29T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "code": "USER_NOT_FOUND",
  "message": "존재하지 않는 회원입니다",
  "path": "/api/v1/users/999/points/balance"
}
```

### 409 Conflict - 재고 부족
```bash
curl -X POST http://localhost:8080/api/v1/users/1/orders \
  -H "Content-Type: application/json" \
  -d '{"items": [{"productId": 3, "quantity": 1}]}'
```
Response:
```json
{
  "timestamp": "2025-10-29T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "OUT_OF_STOCK",
  "message": "재고가 부족합니다",
  "path": "/api/v1/users/1/orders"
}
```

### 409 Conflict - 쿠폰 소진
```bash
curl -X POST http://localhost:8080/api/v1/users/1/coupons/2/issue
```
Response:
```json
{
  "timestamp": "2025-10-29T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "SOLD_OUT",
  "message": "쿠폰이 모두 소진되었습니다",
  "path": "/api/v1/users/1/coupons/2/issue"
}
```

## 주요 API 테스트 예시

### 1. 포인트 충전
```bash
curl -X POST http://localhost:8080/api/v1/users/1/points/charge \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: charge-12345" \
  -d '{"amount": 10000}'
```

### 2. 상품 목록 조회 (페이징)
```bash
curl "http://localhost:8080/api/v1/products?_page=1&_limit=2"
```

### 3. 장바구니 담기
```bash
curl -X POST http://localhost:8080/api/v1/users/1/cart/items \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 2}'
```

### 4. 장바구니 조회
```bash
curl http://localhost:8080/api/v1/users/1/cart/items
```

### 5. 주문 생성
```bash
curl -X POST http://localhost:8080/api/v1/users/1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"productId": 1, "quantity": 2},
      {"productId": 2, "quantity": 1}
    ]
  }'
```

### 6. 주문 결제
```bash
curl -X POST http://localhost:8080/api/v1/users/1/orders/2/payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: pay-67890" \
  -d '{"couponCode": "WELCOME10"}'
```

### 7. 쿠폰 발급
```bash
curl -X POST http://localhost:8080/api/v1/users/1/coupons/1/issue
```

## 참고 사항

### JSON Server 기본 기능
- 자동 CRUD: `GET`, `POST`, `PUT`, `PATCH`, `DELETE` 지원
- 필터링: `?userId=1`, `?status=PENDING`
- 정렬: `?_sort=createdAt&_order=desc`
- 페이징: `?_page=1&_limit=20`
- 전체 텍스트 검색: `?q=키보드`

### 미들웨어
`middleware.js`에서 간단한 에러 처리:
- 400: 잘못된 충전 금액
- 404: 존재하지 않는 사용자 (userId > 100)
- 409: 재고 부족 (productId=3)
- 409: 쿠폰 소진 (couponId=2)

### 한계
- 실제 트랜잭션 처리 없음
- 낙관적 락 동작 없음
- 복잡한 비즈니스 로직 없음
- Idempotency-Key는 로깅만 수행

## 문서
전체 API 명세는 `docs/api_specification.md` 참고

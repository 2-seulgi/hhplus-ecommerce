# RESTful API 명세서

## 공통 사항

### Base URL
```
http://localhost:8080/api/v1
```

### 공통 에러 응답
```json
{
  "timestamp": "2025-10-29T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "OUT_OF_STOCK",
  "message": "재고가 부족합니다",
  "path": "/api/v1/users/1/orders/123/payment"
}
```

### 공통 HTTP 상태 코드
- `200 OK` - 성공
- `201 Created` - 생성 성공
- `204 No Content` - 성공 (응답 본문 없음)
- `400 Bad Request` - 잘못된 요청
- `402 Payment Required` — 결제(포인트) 불충분
- `404 Not Found` - 리소스 없음
- `409 Conflict` - 비즈니스 규칙 위반 (재고 부족, 중복 등)
- `422 Unprocessable Entity` - 검증 실패
- `500 Internal Server Error` - 서버 오류

---

## 1. 포인트 API

### 1.1. 포인트 잔액 조회

**Endpoint:** `GET /users/{userId}/points/balance`

**Description:** 현재 사용자의 포인트 잔액을 조회합니다.

**Path Parameters:**
- `userId`: 회원 ID

**Response:** `200 OK`
```json
{
  "userId": 1,
  "balance": 50000
}
```

**Error Responses:**
- `404 Not Found` - 존재하지 않는 회원 ID

---

### 1.2. 포인트 충전

**Endpoint:** `POST /users/{userId}/points/charge`

**Description:** 포인트를 충전합니다.

**Request Headers:**
```
Idempotency-Key: charge-<uuid> 
```

**Path Parameters:**
- `userId`: 회원 ID

**Request Body:**
```json
{
  "amount": 10000
}
```

**Validation:**
- `amount`: 필수, 1000 이상 1000000 이하

**Response:** `200 OK`
```json
{
  "pointId": 3,
  "userId": 1,
  "pointType": "CHARGE",
  "amount": 10000,
  "balance": 60000,
  "chargedAt": "2025-10-29T10:30:00Z"
}
```

**Error Responses:**
- `400 Bad Request` - 금액이 범위를 벗어남
- `404 Not Found` - 존재하지 않는 회원 ID

---

### 1.3. 포인트 내역 조회

**Endpoint:** `/users/{userId}/points/history?page=0&size=20`

**Description:** 포인트 사용/충전 내역을 조회합니다.

**Path Parameters:**
- `userId`: 회원 ID

**Query Parameters:**
- `page` (optional): 페이지 번호 (default: 0)
- `size` (optional): 페이지 크기 (default: 20)

**Response:** `200 OK`
```json
{
  "content": [
    { "productId": 1, "name": "상품명", "description": "상품 설명", "price": 10000 }
  ],
  "totalElements": 100,
  "totalPages": 5,
  "size": 20,
  "number": 0,
  "first": true,
  "last": false,
  "empty": false
}

```

**Error Responses:**
- `404 Not Found` - 존재하지 않는 회원 ID

---

## 2. 상품 API

### 2.1. 상품 목록 조회

**Endpoint:** `GET /products?page=0&size=20`

**Description:** 상품 목록을 페이징하여 조회합니다. (재고 정보는 포함되지 않음)

**Query Parameters:**
- `page` (optional): 페이지 번호 (default: 0)
- `size` (optional): 페이지 크기 (default: 20)

**Response:** `200 OK`
```json
{
  "content": [
    { "productId": 1, "name": "상품명", "description": "상품 설명", "price": 10000 }
  ],
  "totalElements": 100, "totalPages": 5, "size": 20, "number": 0, "hasNext": true, "hasPrevious": false
}

```

**Error Responses:**
- `400 Bad Request` - 잘못된 페이징 파라미터

---

### 2.2. 상품 상세 조회

**Endpoint:** `GET /products/{productId}`

**Description:** 특정 상품의 상세 정보를 조회합니다. (재고 정보 포함)

**Path Parameters:**
- `productId`: 상품 ID

**Response:** `200 OK`
```json
{
  "productId": 1,
  "name": "상품명",
  "description": "상품 설명",
  "price": 10000,
  "stock": 100,
  "createdAt": "2025-10-01T00:00:00Z"
}
```

**Error Responses:**
- `404 Not Found` - 상품을 찾을 수 없음

---

### 2.3. 상품 재고 조회

**Endpoint:** `GET /products/{productId}/stock`

**Description:** 특정 상품의 실시간 재고를 조회합니다.

**Path Parameters:**
- `productId`: 상품 ID

**Response:** `200 OK`
```json
{
  "productId": 1,
  "stock": 100,
  "stockStatus": "AVAILABLE"
}
```

**stockStatus 값:**
- `AVAILABLE`: 재고 있음
- `LOW_STOCK`: 재고 부족 (10개 이하)
- `OUT_OF_STOCK`: 재고 없음

**Error Responses:**
- `404 Not Found` - 상품을 찾을 수 없음

---

### 2.4. 인기 상품 조회

**Endpoint:** `GET /products/top?period=3d&limit=5`

**Description:** 최근 3일간 판매량 기준 Top5 상품을 조회합니다.

**Query Parameters:**
- `period` (optional): 기간 (default: "3d")
- `limit` (optional): 조회 개수 (default: 5)

**Response:** `200 OK`
```json
{
  "products": [
    { "productId": 1, "name": "인기상품1", "price": 10000, "salesCount": 150 },
    { "productId": 2, "name": "인기상품2", "price": 20000, "salesCount": 120 }
  ]
}

```

---

## 3. 장바구니 API
> 총액은 저장하지 않고 응답 시 서버에서 계산해 내려줌.
> 장바구니 항목 식별은 **cartItemId(PK)**로 조작.

### 3.1. 장바구니 담기

**Endpoint:** `POST /users/{userId}/cart/items`

**Description:** 상품을 장바구니에 담습니다.

**Path Parameters:**
- `userId`: 회원 ID

**Request Body:**
```json
{
  "productId": 1,
  "quantity": 2
}
```

**Validation:**
- `productId`: 필수
- `quantity`: 필수, 1 이상

**Response:** `200 OK`
```json
{
  "item": {
    "cartItemId": 11,
    "productId": 1,
    "productName": "상품명",
    "unitPrice": 10000,
    "quantity": 2,
    "subtotal": 20000,
    "stock": 100,
    "stockOk": true
  },
  "summary": { "totalAmount": 30000, "itemCount": 2 }
}

```

**Error Responses:**
- `400 Bad Request` - 잘못된 요청
- `404 Not Found` - 회원 또는 상품을 찾을 수 없음

---

### 3.2. 장바구니 조회

**Endpoint:** `GET /users/{userId}/cart/items`

**Description:** 장바구니에 담긴 상품 목록을 조회합니다.

**Path Parameters:**
- `userId`: 회원 ID

**Response:** `200 OK`
```json
{
  "items": [
    { "cartItemId": 11, "productId": 1, "productName": "A", "unitPrice": 10000, "quantity": 2, "subtotal": 20000, "stock": 100, "stockOk": true },
    { "cartItemId": 12, "productId": 2, "productName": "B", "unitPrice": 20000, "quantity": 1, "subtotal": 20000, "stock": 1,   "stockOk": false }
  ],
  "summary": { "totalAmount": 40000, "itemCount": 2 }
}
```

**Error Responses:**
- `404 Not Found` - 존재하지 않는 회원 ID

---

### 3.3. 장바구니 삭제

**Endpoint:** `DELETE /users/{userId}/cart/items/{cartItemId}`

**Description:** 장바구니에서 특정 상품을 삭제하고, 삭제 후 전체 장바구니 상태를 반환합니다.

**Path Parameters:**
- `userId`: 회원 ID
- `cartItemId`: 장바구니 항목 ID

**Response:** `200 OK`
```json
{
  "items": [
    {
      "cartItemId": 11,
      "productId": 1,
      "productName": "남은 상품",
      "unitPrice": 10000,
      "quantity": 2,
      "subtotal": 20000,
      "stock": 100,
      "stockOk": true
    }
  ],
  "summary": {
    "totalAmount": 20000,
    "itemCount": 1
  }
}
```

**Note:** 장바구니가 비어있으면 `items: [], summary: { totalAmount: 0, itemCount: 0 }` 반환

**Error Responses:**
- `404 Not Found` - 회원 또는 장바구니 항목을 찾을 수 없음

---

### 3.4. 장바구니 수량 변경

**Endpoint:** `PATCH /users/{userId}/cart/items/{cartItemId}`

**Description:** 장바구니 상품의 수량을 변경하고, 변경 후 전체 장바구니 상태를 반환합니다. 수량을 0으로 설정하면 해당 상품이 자동으로 삭제됩니다.

**Path Parameters:**
- `userId`: 회원 ID
- `cartItemId`: 장바구니 항목 ID

**Request Body:**
```json
{
  "quantity": 5
}
```

**Validation:**
- `quantity`: 필수, 0 이상 (0이면 자동 삭제)

**Response:** `200 OK`
```json
{
  "items": [
    {
      "cartItemId": 11,
      "productId": 1,
      "productName": "상품명",
      "unitPrice": 10000,
      "quantity": 5,
      "subtotal": 50000,
      "stock": 100,
      "stockOk": true
    },
    {
      "cartItemId": 12,
      "productId": 2,
      "productName": "다른 상품",
      "unitPrice": 20000,
      "quantity": 1,
      "subtotal": 20000,
      "stock": 50,
      "stockOk": true
    }
  ],
  "summary": {
    "totalAmount": 70000,
    "itemCount": 2
  }
}
```

**수량 0 처리 예시:**
Request: { "quantity": 0 }
Response: 해당 상품이 삭제된 전체 장바구니
```json
{
  "items": [
    {
      "cartItemId": 12,
      "productId": 2,
      "productName": "다른 상품",
      "unitPrice": 20000,
      "quantity": 1,
      "subtotal": 20000,
      "stock": 50,
      "stockOk": true
    }
  ],
  "summary": {
    "totalAmount": 20000,
    "itemCount": 1
  }
}
```

**Error Responses:**
- `400 Bad Request` - 잘못된 요청 (음수 수량 등)
- `404 Not Found` - 회원 또는 장바구니 항목을 찾을 수 없음

---

## 4. 주문 API

### 4.1. 주문 생성

**Endpoint:** `POST /users/{userId}/orders`

**Description:** 장바구니의 상품으로 주문을 생성합니다.

**Path Parameters:**
- `userId`: 회원 ID

**Response:** `201 Created`
```json
{
  "orderId": 12345,
  "userId": 1,
  "status": "PENDING",
  "totalAmount": 30000,
  "expiresAt": "2025-10-29T11:00:00Z",
  "items": [
    { "productId": 1, "productName": "상품명", "unitPrice": 10000, "quantity": 2 }
  ]
}
```

**Error Responses:**
- `400 Bad Request` - 장바구니가 비어있음
- `404 Not Found` - 존재하지 않는 회원 ID
- `409 OUT_OF_STOCK` — 주문 생성 시점에 재고가 0인 상품이 포함된 경우 주문 생성 거부(실제 재고 차감은 결제 시점)

---

### 4.2. 주문 내역 조회

**Endpoint:** `GET /users/{userId}/orders`

**Description:** 사용자의 주문 내역을 조회합니다.

**Path Parameters:**
- `userId`: 회원 ID

**Response:** `200 OK`
```json
{
  "orders": [
    {
      "orderId": 12345,
      "userId": 1,
      "status": "CONFIRMED",
      "totalAmount": 30000,
      "createdAt": "2025-10-29T10:00:00Z",
      "items": [
        { "productId": 1, "productName": "상품명", "unitPrice": 10000, "quantity": 2 }
      ]
    }
  ],
  "totalElements": 1, "totalPages": 1, "size": 20, "number": 0, "hasNext": false, "hasPrevious": false
}

```

**Error Responses:**
- `404 Not Found` - 존재하지 않는 회원 ID

---

### 4.3. 주문 상세 조회

**Endpoint:** `GET /users/{userId}/orders/{orderId}`

**Description:** 특정 주문의 상세 정보를 조회합니다.

**Path Parameters:**
- `userId`: 회원 ID
- `orderId`: 주문 ID

**Response:** `200 OK`
```json
{
  "orderId": 12345,
  "userId": 1,
  "status": "CONFIRMED",
  "totalAmount": 30000,
  "createdAt": "2025-10-29T10:00:00Z",
  "expiresAt": "2025-10-29T10:30:00Z",
  "items": [
    { "productId": 1, "productName": "상품명", "unitPrice": 10000, "quantity": 2 }
  ]
}
```

**Error Responses:**
- `404 Not Found` - 주문을 찾을 수 없음

---

## 5. 결제 API

### 5.1. 주문 결제

**Endpoint:** `POST /users/{userId}/orders/{orderId}/payment`

**Description:** 주문을 결제합니다.

**Path Parameters:**
- `userId`: 회원 ID
- `orderId`: 주문 ID

**Request Headers:**
```
Idempotency-Key: pay-<uuid>   // 필수, 이중 결제 방지
```

**Request Body:**
```json
{ "couponCode": "WELCOME10" }
```

**Response:** `200 OK`
```json
{
  "orderId": 12345,
  "userId": 1,
  "status": "CONFIRMED",
  "totalAmount": 30000,
  "discountAmount": 5000,
  "finalAmount": 25000,
  "remainingBalance": 35000,
  "paidAt": "2025-10-29T10:30:00Z"
}
```

**Error Responses:**
- `402 Payment Required` - 포인트 부족
  ```json
  {
    "timestamp": "2025-10-29T10:30:00Z",
    "status": 402,
    "error": "Payment Required",
    "code": "INSUFFICIENT_POINT",
    "message": "포인트가 부족합니다. 필요: 25000, 보유: 20000",
    "path": "/api/v1/users/1/orders/12345/payment"
  }
  ```
- `404 Not Found` - 주문을 찾을 수 없음
- `409 Conflict` -  ORDER_EXPIRED | OUT_OF_STOCK | COUPON_INVALID | COUPON_EXPIRED | COUPON_ALREADY_USED
  ```json
  {
    "timestamp": "2025-10-29T10:30:00Z",
    "status": 409,
    "error": "Conflict",
    "message": "주문이 만료되었습니다",
    "code": "ORDER_EXPIRED",
    "path": "/api/v1/users/1/orders/12345/payment"
  }
  ```
  - 가능한 `code` 값:
    - `ORDER_EXPIRED` - 주문 만료
    - `OUT_OF_STOCK` - 재고 부족
    - `COUPON_INVALID` - 쿠폰 사용 불가
    - `COUPON_EXPIRED` - 쿠폰 만료
    - `COUPON_ALREADY_USED` - 쿠폰 이미 사용됨

---

### 5.2. 주문 환불

**Endpoint:** `POST /users/{userId}/orders/{orderId}/refund`

**Description:** 결제 완료된 주문을 환불합니다.

**Request Headers:**
```
Idempotency-Key: refund-<uuid>   // 필수
```

**Path Parameters:**
- `userId`: 회원 ID
- `orderId`: 주문 ID

**Response:** `200 OK`
```json
{
  "orderId": 12345,
  "userId": 1,
  "status": "REFUNDED",
  "refundedAmount": 25000,
  "currentBalance": 60000,
  "refundedAt": "2025-10-29T11:00:00Z"
}
```

**Error Responses:**
- `404 Not Found` - 주문을 찾을 수 없음
- `409 Conflict` - 환불 불가능한 상태
  ```json
  {
    "timestamp": "2025-10-29T11:00:00Z",
    "status": 409,
    "error": "Conflict",
    "message": "환불 불가능한 주문 상태입니다",
    "code": "INVALID_ORDER_STATUS",
    "path": "/api/v1/users/1/orders/12345/refund"
  }
  ```

---

## 6. 쿠폰 API

### 6.1. 선착순 쿠폰 발급

**Endpoint:** `POST /users/{userId}/coupons/{couponId}/issue`

**Description:** 선착순 쿠폰을 발급받습니다.

**Path Parameters:**
- `userId`: 회원 ID
- `couponId`: 쿠폰 ID

**Response:** `200 OK`
```json
{
  "userCouponId": 456,
  "userId": 1,
  "couponId": 123,
  "couponName": "5000원 할인 쿠폰",
  "discountType": "FIXED",
  "discountValue": 5000,
  "useStartAt": "2025-10-29T00:00:00Z",
  "useEndAt": "2025-11-30T23:59:59Z",
  "issuedAt": "2025-10-29T10:30:00Z"
}
```

**Error Responses:**
- `404 Not Found` - 회원 또는 쿠폰을 찾을 수 없음
- `409 Conflict` - 발급 불가
  ```json
  {
    "timestamp": "2025-10-29T10:30:00Z",
    "status": 409,
    "error": "Conflict",
    "message": "쿠폰이 모두 소진되었습니다",
    "code": "SOLD_OUT",
    "path": "/api/v1/users/1/coupons/123/issue"
  }
  ```
  - 가능한 `code` 값:
    - `SOLD_OUT` - 발급 수량 소진
    - `ALREADY_ISSUED` - 이미 발급받음
    - `ISSUE_PERIOD_EXPIRED` - 발급 기간 만료

---

### 6.2. 보유 쿠폰 조회

**Endpoint:** `GET /users/{userId}/coupons?available=false`

**Description:** 사용자가 보유한 쿠폰을 조회합니다.

**Path Parameters:**
- `userId`: 회원 ID

**Query Parameters:**
- `available` (optional): 사용 가능한 쿠폰만 조회 (default: false)

**Response:** `200 OK`
```json
{
  "coupons": [
    {
      "userCouponId": 456,
      "userId": 1,
      "couponId": 123,
      "couponName": "5000원 할인 쿠폰",
      "discountType": "FIXED",
      "discountValue": 5000,
      "used": false,
      "useStartAt": "2025-10-29T00:00:00Z",
      "useEndAt": "2025-11-30T23:59:59Z",
      "issuedAt": "2025-10-29T10:30:00Z"
    }
  ]
}
```

**Error Responses:**
- `404 Not Found` - 존재하지 않는 회원 ID

---

## 7. 에러 코드 정의

### 비즈니스 에러 코드 (409 Conflict)

| 코드 | 설명          | HTTP Status |
|------|-------------|-------------|
| `ORDER_EXPIRED` | 주문이 만료됨     | 409         |
| `OUT_OF_STOCK` | 재고 부족       | 409         |
| `COUPON_INVALID` | 쿠폰 사용 불가    | 409         |
| `COUPON_EXPIRED` | 쿠폰 만료       | 409         |
| `COUPON_ALREADY_USED` | 쿠폰 이미 사용됨   | 409         |
| `SOLD_OUT` | 쿠폰 발급 수량 소진 | 409         |
| `ALREADY_ISSUED` | 이미 발급받은 쿠폰  | 409         |
| `ISSUE_PERIOD_EXPIRED` | 쿠폰 발급 기간 만료 | 409         |
| `INVALID_ORDER_STATUS` | 잘못된 주문 상태   | 409         |


### 결제 에러 코드

| 코드 | 설명 | HTTP Status |
|------|------|-------------|
| `INSUFFICIENT_POINT` | 포인트 부족 | 402 |

---

## 8. 페이징 공통 응답 형식

```json
{
  "content": [],
  "totalElements": 100,
  "totalPages": 5,
  "size": 20,
  "number": 0,
  "hasNext": true,
  "hasPrevious": false
}
```

- `content`: 실제 데이터 배열
- `totalElements`: 전체 요소 개수
- `totalPages`: 전체 페이지 수
- `size`: 페이지 크기
- `number`: 현재 페이지 번호 (0부터 시작)
- `hasNext`: 다음 페이지 존재 여부
- `hasPrevious`: 이전 페이지 존재 여부

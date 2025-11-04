module.exports = (req, res, next) => {
  // 간단한 에러 처리 미들웨어

  // 1. 400 Bad Request - 필수 파라미터 누락
  if (req.method === 'POST' && req.path.includes('/charge')) {
    const { amount } = req.body;
    if (!amount || amount < 1000 || amount > 1000000) {
      return res.status(400).json({
        timestamp: new Date().toISOString(),
        status: 400,
        error: "Bad Request",
        code: "INVALID_AMOUNT",
        message: "충전 금액은 1,000원 ~ 1,000,000원 사이여야 합니다",
        path: req.path
      });
    }
  }

  // 2. 404 Not Found - 존재하지 않는 리소스
  if (req.method === 'GET' && req.path.match(/\/users\/(\d+)/)) {
    const userId = parseInt(req.path.match(/\/users\/(\d+)/)[1]);
    if (userId > 100) {
      return res.status(404).json({
        timestamp: new Date().toISOString(),
        status: 404,
        error: "Not Found",
        code: "USER_NOT_FOUND",
        message: "존재하지 않는 회원입니다",
        path: req.path
      });
    }
  }

  // 3. 409 Conflict - 재고 부족 (간단한 체크)
  if (req.method === 'POST' && req.path.includes('/orders')) {
    // 재고가 0인 상품이 있으면 주문 생성 거부 (간단한 로직)
    const hasOutOfStock = req.body.items && req.body.items.some(item => item.productId === 3);
    if (hasOutOfStock) {
      return res.status(409).json({
        timestamp: new Date().toISOString(),
        status: 409,
        error: "Conflict",
        code: "OUT_OF_STOCK",
        message: "재고가 부족합니다",
        path: req.path
      });
    }
  }

  // 4. 409 Conflict - 쿠폰 소진 (간단한 체크)
  if (req.method === 'POST' && req.path.includes('/coupons') && req.path.includes('/issue')) {
    const couponId = parseInt(req.path.match(/\/coupons\/(\d+)/)?.[1] || 0);
    // couponId 2는 이미 소진된 것으로 가정
    if (couponId === 2) {
      return res.status(409).json({
        timestamp: new Date().toISOString(),
        status: 409,
        error: "Conflict",
        code: "SOLD_OUT",
        message: "쿠폰이 모두 소진되었습니다",
        path: req.path
      });
    }
  }

  // Idempotency-Key 헤더 체크 (로깅만)
  if (req.headers['idempotency-key']) {
    console.log(`[Idempotency-Key]: ${req.headers['idempotency-key']}`);
  }

  // 응답 시간 추가
  res.setHeader('X-Response-Time', Date.now());

  next();
};

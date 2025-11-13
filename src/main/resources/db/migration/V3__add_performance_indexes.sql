-- ========================================
-- Performance Optimization Indexes
-- Version: 3.0
-- 성능 분석 보고서 기반 인덱스 추가
-- ========================================

-- 1. 인기 상품 조회 최적화
-- orders 테이블: status + paid_at 복합 인덱스
CREATE INDEX idx_order_status_paid ON orders (status, paid_at DESC);

-- 2. 주문 목록 조회 최적화
-- orders 테이블: user_id + created_at 복합 인덱스
CREATE INDEX idx_order_user_created ON orders (user_id, created_at DESC);

-- 3. 인기 상품 조회 커버링 인덱스
-- order_items 테이블: order_id + product_id + quantity 복합 인덱스
CREATE INDEX idx_order_item_covering ON order_items (order_id, product_id, quantity);

-- 4. 사용자 쿠폰 조회 최적화
-- user_coupons 테이블: user_id + used + issued_at 복합 인덱스
CREATE INDEX idx_user_coupon_user_used_issued ON user_coupons (user_id, used, issued_at DESC);
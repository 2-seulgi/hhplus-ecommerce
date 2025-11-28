-- 상품 데이터
INSERT IGNORE INTO product (id, name, description, price, stock, version, created_at, updated_at) VALUES
(1, '무선 이어폰', '고음질 블루투스 이어폰', 89000, 100, 0, NOW(), NOW()),
(2, '스마트워치', '건강 관리 스마트워치', 250000, 50, 0, NOW(), NOW()),
(3, '노트북 거치대', '알루미늄 노트북 스탠드', 45000, 200, 0, NOW(), NOW()),
(4, '기계식 키보드', '청축 기계식 키보드', 120000, 30, 0, NOW(), NOW()),
(5, '모니터 암', '듀얼 모니터 암', 89000, 80, 0, NOW(), NOW());

-- 사용자 데이터
INSERT IGNORE INTO users (id, name, balance, created_at, updated_at) VALUES
(1, '홍길동', 500000, NOW(), NOW()),
(2, '김철수', 300000, NOW(), NOW());

-- 주문 데이터 (CONFIRMED 상태)
INSERT IGNORE INTO orders (id, user_id, total_amount, status, paid_at, created_at, updated_at) VALUES
(1, 1, 178000, 'CONFIRMED', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
(2, 1, 250000, 'CONFIRMED', DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW()),
(3, 2, 89000, 'CONFIRMED', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW());

-- 주문 아이템 데이터
INSERT IGNORE INTO order_items (id, order_id, product_id, product_name, unit_price, quantity, created_at) VALUES
(1, 1, 1, '무선 이어폰', 89000, 2, NOW()),
(2, 2, 2, '스마트워치', 250000, 1, NOW()),
(3, 3, 1, '무선 이어폰', 89000, 1, NOW());
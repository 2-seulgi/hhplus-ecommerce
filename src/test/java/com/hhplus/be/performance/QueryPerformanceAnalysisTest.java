package com.hhplus.be.performance;

import com.hhplus.be.cart.domain.model.CartItem;
import com.hhplus.be.cart.domain.repository.CartRepository;
import com.hhplus.be.coupon.domain.model.Coupon;
import com.hhplus.be.coupon.domain.model.DiscountType;
import com.hhplus.be.coupon.domain.repository.CouponRepository;
import com.hhplus.be.order.service.OrderService;
import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.domain.repository.ProductRepository;
import com.hhplus.be.product.service.ProductService;
import com.hhplus.be.product.service.dto.TopProductQuery;
import com.hhplus.be.testsupport.IntegrationTestSupport;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.user.domain.repository.UserRepository;
import com.hhplus.be.usercoupon.domain.model.UserCoupon;
import com.hhplus.be.usercoupon.domain.repository.UserCouponRepository;
import com.hhplus.be.usercoupon.service.UserCouponService;
import com.hhplus.be.usercoupon.service.dto.GetUserCouponsQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 쿼리 성능 분석을 위한 EXPLAIN 실행 테스트
 *
 * 목적:
 * - 실제 쿼리 실행 계획 수집
 * - 인덱스 사용 여부 확인
 * - 성능 병목 지점 식별
 */
@SpringBootTest
@ActiveProfiles("test")
class QueryPerformanceAnalysisTest extends IntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserCouponService userCouponService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private Clock clock;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    @Transactional
    @DisplayName("쿼리 1: 인기 상품 조회 - EXPLAIN 분석")
    void explainTopProductsQuery() {
        // Given: 테스트 데이터 준비
        User user = User.create("분석유저", "analysis@test.com", 1000000);
        user = userRepository.save(user);

        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Product product = Product.create("상품" + i, "설명" + i, 10000 + i * 1000, 100);
            products.add(productRepository.save(product));
        }

        Instant now = Instant.now(clock);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        // 주문 데이터 생성 (100개)
        for (int i = 0; i < 100; i++) {
            CartItem cartItem = CartItem.create(user.getId(), products.get(i % 10).getId(), 1);
            cartRepository.save(cartItem);
            orderService.createFromCart(user.getId());
            cartRepository.deleteAllByUserId(user.getId());
        }

        // When: 실제 쿼리 실행 (쿼리 확인용)
        System.out.println("\n=== 인기 상품 조회 쿼리 실행 ===");
        productService.getTopProducts(new TopProductQuery("3d", 5));

        // Then: EXPLAIN 실행
        System.out.println("\n=== EXPLAIN 분석: 인기 상품 조회 ===");
        String explainQuery = """
            EXPLAIN
            SELECT oi.product_id, p.name, SUM(oi.quantity) as total_sales
            FROM order_items oi
            INNER JOIN orders o ON oi.order_id = o.id
            INNER JOIN product p ON oi.product_id = p.id
            WHERE o.status = 'CONFIRMED'
              AND o.paid_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
            GROUP BY oi.product_id, p.name
            ORDER BY total_sales DESC
            LIMIT 5
            """;

        List<Map<String, Object>> explainResult = jdbcTemplate.queryForList(explainQuery);
        printExplainResult("인기 상품 조회", explainResult);
    }

    @Test
    @Transactional
    @DisplayName("쿼리 2: 사용자 쿠폰 조회 - EXPLAIN 분석")
    void explainUserCouponsQuery() {
        // Given: 테스트 데이터 준비
        User user = User.create("쿠폰유저", "coupon_user@test.com", 100000);
        user = userRepository.save(user);

        Instant now = Instant.now(clock);
        List<Coupon> coupons = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Coupon coupon = Coupon.create(
                    "COUPON_" + System.currentTimeMillis() + "_" + i,
                    "쿠폰 " + i,
                    DiscountType.FIXED,
                    5000,
                    100,
                    0,
                    now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS),
                    now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS)
            );
            coupons.add(couponRepository.save(coupon));
        }

        for (Coupon coupon : coupons) {
            UserCoupon userCoupon = UserCoupon.create(user.getId(), coupon.getId(), now);
            userCouponRepository.save(userCoupon);
        }

        // When: 실제 쿼리 실행
        System.out.println("\n=== 사용자 쿠폰 조회 쿼리 실행 ===");
        userCouponService.getUserCoupons(new GetUserCouponsQuery(user.getId(), null));

        // Then: EXPLAIN 실행
        System.out.println("\n=== EXPLAIN 분석: 사용자 쿠폰 조회 (N+1 문제) ===");

        // 1. UserCoupon 조회
        String explainUserCoupon = "EXPLAIN SELECT * FROM user_coupons WHERE user_id = " + user.getId();
        List<Map<String, Object>> result1 = jdbcTemplate.queryForList(explainUserCoupon);
        printExplainResult("UserCoupon 조회", result1);

        // 2. Coupon 개별 조회 (N+1 발생)
        System.out.println("\n=== N+1 문제: 각 UserCoupon마다 Coupon 개별 조회 ===");
        String explainCoupon = "EXPLAIN SELECT * FROM coupons WHERE id = " + coupons.get(0).getId();
        List<Map<String, Object>> result2 = jdbcTemplate.queryForList(explainCoupon);
        printExplainResult("Coupon 개별 조회 (50번 반복)", result2);

        // 3. 개선안: IN 절 사용
        System.out.println("\n=== 개선안: IN 절로 일괄 조회 ===");
        String couponIds = coupons.stream()
                .limit(10)
                .map(c -> String.valueOf(c.getId()))
                .reduce((a, b) -> a + "," + b)
                .orElse("0");
        String explainBulk = "EXPLAIN SELECT * FROM coupons WHERE id IN (" + couponIds + ")";
        List<Map<String, Object>> result3 = jdbcTemplate.queryForList(explainBulk);
        printExplainResult("Coupon 일괄 조회 (IN 절)", result3);
    }

    @Test
    @Transactional
    @DisplayName("쿼리 3: 주문 목록 조회 - EXPLAIN 분석")
    void explainOrderListQuery() {
        // Given: 테스트 데이터 준비
        User user = User.create("주문유저", "order_user@test.com", 1000000);
        user = userRepository.save(user);

        Product product = Product.create("테스트상품", "설명", 10000, 100);
        product = productRepository.save(product);

        // 100개 주문 생성
        for (int i = 0; i < 100; i++) {
            CartItem cartItem = CartItem.create(user.getId(), product.getId(), 1);
            cartRepository.save(cartItem);
            orderService.createFromCart(user.getId());
            cartRepository.deleteAllByUserId(user.getId());
        }

        // When: EXPLAIN 실행
        System.out.println("\n=== EXPLAIN 분석: 주문 목록 조회 ===");

        // 1. Orders 조회
        String explainOrders = "EXPLAIN SELECT * FROM orders WHERE user_id = " + user.getId() + " ORDER BY created_at DESC";
        List<Map<String, Object>> result1 = jdbcTemplate.queryForList(explainOrders);
        printExplainResult("Orders 조회", result1);

        // 2. OrderItems 조회 (IN 절 - 성능 이슈 가능)
        System.out.println("\n=== OrderItems 조회 ===");
        String explainOrderItems = """
            EXPLAIN
            SELECT * FROM order_items
            WHERE order_id IN (
                SELECT id FROM orders WHERE user_id = %d
            )
            """.formatted(user.getId());
        List<Map<String, Object>> result2 = jdbcTemplate.queryForList(explainOrderItems);
        printExplainResult("OrderItems IN 절 조회", result2);

        // 3. 개선안: JOIN 사용
        System.out.println("\n=== 개선안: JOIN으로 한 번에 조회 ===");
        String explainJoin = """
            EXPLAIN
            SELECT o.*, oi.*
            FROM orders o
            LEFT JOIN order_items oi ON o.id = oi.order_id
            WHERE o.user_id = %d
            ORDER BY o.created_at DESC
            """.formatted(user.getId());
        List<Map<String, Object>> result3 = jdbcTemplate.queryForList(explainJoin);
        printExplainResult("JOIN으로 조회", result3);
    }

    @Test
    @DisplayName("인덱스 현황 확인")
    void showIndexes() {
        System.out.println("\n=== 데이터베이스 인덱스 현황 ===\n");

        String[] tables = {"orders", "order_items", "user_coupons", "coupons", "product"};

        for (String table : tables) {
            System.out.println("📊 테이블: " + table);
            String query = "SHOW INDEX FROM " + table;
            try {
                List<Map<String, Object>> indexes = jdbcTemplate.queryForList(query);
                for (Map<String, Object> index : indexes) {
                    System.out.printf("  - %s (%s) on %s | Cardinality: %s | Type: %s\n",
                            index.get("Key_name"),
                            index.get("Non_unique").equals(0) ? "UNIQUE" : "NON-UNIQUE",
                            index.get("Column_name"),
                            index.get("Cardinality"),
                            index.get("Index_type")
                    );
                }
            } catch (Exception e) {
                System.out.println("  ⚠ 테이블을 찾을 수 없음");
            }
            System.out.println();
        }
    }

    private void printExplainResult(String queryName, List<Map<String, Object>> result) {
        System.out.println("\n📊 " + queryName);
        System.out.println("─".repeat(120));

        if (result.isEmpty()) {
            System.out.println("결과 없음");
            return;
        }

        // 헤더 출력
        Map<String, Object> first = result.get(0);
        String header = String.format("%-5s | %-10s | %-20s | %-10s | %-30s | %-10s | %-10s",
                "id", "type", "table", "key", "Extra", "rows", "filtered");
        System.out.println(header);
        System.out.println("─".repeat(120));

        // 데이터 출력
        for (Map<String, Object> row : result) {
            String line = String.format("%-5s | %-10s | %-20s | %-10s | %-30s | %-10s | %-10s",
                    getValue(row, "id"),
                    getValue(row, "select_type"),
                    getValue(row, "table"),
                    getValue(row, "key"),
                    getValue(row, "Extra"),
                    getValue(row, "rows"),
                    getValue(row, "filtered")
            );
            System.out.println(line);
        }
        System.out.println("─".repeat(120));

        // 성능 이슈 분석
        analyzePerformanceIssues(result);
    }

    private String getValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value.toString() : "NULL";
    }

    private void analyzePerformanceIssues(List<Map<String, Object>> explainResult) {
        System.out.println("\n🔍 성능 분석:");

        for (Map<String, Object> row : explainResult) {
            String type = getValue(row, "type");
            String key = getValue(row, "key");
            String extra = getValue(row, "Extra");
            String rows = getValue(row, "rows");

            // Full Table Scan 감지
            if ("ALL".equals(type)) {
                System.out.println("  ⚠️ Full Table Scan 발생! 인덱스 추가 필요");
            }

            // 인덱스 미사용 감지
            if ("NULL".equals(key)) {
                System.out.println("  ⚠️ 인덱스 미사용! 적절한 인덱스 생성 필요");
            }

            // Using filesort 감지
            if (extra != null && extra.contains("Using filesort")) {
                System.out.println("  ⚠️ Filesort 발생! ORDER BY 최적화 필요");
            }

            // Using temporary 감지
            if (extra != null && extra.contains("Using temporary")) {
                System.out.println("  ⚠️ 임시 테이블 사용! GROUP BY 최적화 필요");
            }

            // 대량 행 스캔 감지
            try {
                int rowCount = Integer.parseInt(rows);
                if (rowCount > 1000) {
                    System.out.println("  ⚠️ 대량 행 스캔 (" + rowCount + " rows)! 쿼리 최적화 필요");
                }
            } catch (NumberFormatException ignored) {
            }

            // 좋은 패턴 감지
            if (extra != null && extra.contains("Using index")) {
                System.out.println("  ✅ 커버링 인덱스 사용 중 (최적)");
            }
        }
    }
}
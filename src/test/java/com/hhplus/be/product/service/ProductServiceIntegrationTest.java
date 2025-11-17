package com.hhplus.be.product.service;

import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.domain.repository.OrderRepository;
import com.hhplus.be.orderitem.domain.model.OrderItem;
import com.hhplus.be.orderitem.domain.repository.OrderItemRepository;
import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.domain.repository.ProductRepository;
import com.hhplus.be.product.service.dto.TopProductQuery;
import com.hhplus.be.product.service.dto.TopProductResult;
import com.hhplus.be.testsupport.IntegrationTestSupport;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 상품 Service 통합 테스트
 *
 * - 인기 상품 조회: ORDER_ITEMS와 ORDERS 테이블 JOIN 후 집계 쿼리 성능
 */
class ProductServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    private User testUser;
    private List<Product> testProducts;

    @BeforeEach
    void setUp() {
        // 1) 기존 데이터 전부 삭제
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트 유저 생성
        testUser = User.create(
                "상품테스트유저",
                "product_test_" + System.currentTimeMillis() + "@test.com",
                100000
        );
        testUser = userRepository.save(testUser);

        // 테스트 상품 10개 생성
        testProducts = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Product product = Product.create(
                    "상품" + i,
                    "상품 설명 " + i,
                    10000 + (i * 1000),
                    100
            );
            testProducts.add(productRepository.save(product));
        }
    }


    @Test
    @DisplayName("인기 상품 조회 - 최근 3일간 판매량 기준 정렬")
    void getTopProducts_Last3Days_SortedBySales() {
        // Given: 3일 이내 주문 생성
        Instant now = Instant.now(clock);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        // 상품1: 10개 판매 (1위)
        createConfirmedOrder(testProducts.get(0), 10, twoDaysAgo);

        // 상품2: 7개 판매 (2위)
        createConfirmedOrder(testProducts.get(1), 7, twoDaysAgo);

        // 상품3: 5개 판매 (3위)
        createConfirmedOrder(testProducts.get(2), 5, twoDaysAgo);

        // 상품4: 3개 판매 (4위)
        createConfirmedOrder(testProducts.get(3), 3, twoDaysAgo);

        // 상품5: 1개 판매 (5위)
        createConfirmedOrder(testProducts.get(4), 1, twoDaysAgo);

        // When: 인기 상품 Top 5 조회
        TopProductQuery query = new TopProductQuery("3d", 5);
        TopProductResult result = productService.getTopProducts(query);

        // Then: 판매량 순으로 정렬
        assertThat(result.products()).hasSize(5);
        assertThat(result.products().get(0).productId()).isEqualTo(testProducts.get(0).getId());
        assertThat(result.products().get(0).salesCount()).isEqualTo(10);
        assertThat(result.products().get(1).productId()).isEqualTo(testProducts.get(1).getId());
        assertThat(result.products().get(1).salesCount()).isEqualTo(7);
        assertThat(result.products().get(2).productId()).isEqualTo(testProducts.get(2).getId());
        assertThat(result.products().get(2).salesCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("인기 상품 조회 - 기간 외 주문은 제외")
    void getTopProducts_OnlyIncludeOrdersWithinPeriod() {
        // Given: 3일 이내/이외 주문 생성
        Instant now = Instant.now(clock);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant fiveDaysAgo = now.minus(5, ChronoUnit.DAYS);

        // 상품1: 3일 이내 10개 판매
        createConfirmedOrder(testProducts.get(0), 10, twoDaysAgo);

        // 상품2: 5일 전 100개 판매 (제외되어야 함)
        createConfirmedOrder(testProducts.get(1), 100, fiveDaysAgo);

        // When: 인기 상품 Top 5 조회 (기본 3일)
        TopProductQuery query = new TopProductQuery(null, 5);
        TopProductResult result = productService.getTopProducts(query);

        // Then: 3일 이내 주문만 집계
        assertThat(result.products()).hasSize(1);
        assertThat(result.products().get(0).productId()).isEqualTo(testProducts.get(0).getId());
        assertThat(result.products().get(0).salesCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("인기 상품 조회 - CONFIRMED 주문만 집계")
    void getTopProducts_OnlyConfirmedOrders() {
        // Given: CONFIRMED/PENDING/CANCELLED 주문 생성
        Instant now = Instant.now(clock);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        // 상품1: CONFIRMED 10개
        createConfirmedOrder(testProducts.get(0), 10, twoDaysAgo);

        // 상품2: PENDING 100개 (제외되어야 함)
        createPendingOrder(testProducts.get(1), 100, twoDaysAgo);

        // 상품3: CANCELLED 50개 (제외되어야 함)
        createCancelledOrder(testProducts.get(2), 50, twoDaysAgo);

        // When: 인기 상품 Top 5 조회
        TopProductQuery query = new TopProductQuery("3d", 5);
        TopProductResult result = productService.getTopProducts(query);

        // Then: CONFIRMED 주문만 집계
        assertThat(result.products()).hasSize(1);
        assertThat(result.products().get(0).productId()).isEqualTo(testProducts.get(0).getId());
        assertThat(result.products().get(0).salesCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("인기 상품 조회 - 여러 주문에 걸친 판매량 합산")
    void getTopProducts_SumSalesAcrossMultipleOrders() {
        // Given: 동일 상품에 대한 여러 주문
        Instant now = Instant.now(clock);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);

        // 상품1: 3개 주문 (5 + 3 + 2 = 10개)
        createConfirmedOrder(testProducts.get(0), 5, twoDaysAgo);
        createConfirmedOrder(testProducts.get(0), 3, oneDayAgo);
        createConfirmedOrder(testProducts.get(0), 2, now);

        // When: 인기 상품 조회
        TopProductQuery query = new TopProductQuery("3d", 5);
        TopProductResult result = productService.getTopProducts(query);

        // Then: 판매량 합산
        assertThat(result.products()).hasSize(1);
        assertThat(result.products().get(0).salesCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("인기 상품 조회 - limit 적용")
    void getTopProducts_LimitResults() {
        // Given: 10개 상품 모두 판매
        Instant now = Instant.now(clock);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        for (int i = 0; i < 10; i++) {
            createConfirmedOrder(testProducts.get(i), 10 - i, twoDaysAgo);
        }

        // When: Top 3 조회
        TopProductQuery query = new TopProductQuery("3d", 3);
        TopProductResult result = productService.getTopProducts(query);

        // Then: 3개만 반환
        assertThat(result.products()).hasSize(3);
        assertThat(result.products().get(0).salesCount()).isEqualTo(10);
        assertThat(result.products().get(1).salesCount()).isEqualTo(9);
        assertThat(result.products().get(2).salesCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("인기 상품 조회 - 판매 이력 없을 때 빈 리스트 반환")
    void getTopProducts_NoSales_ReturnsEmptyList() {
        // Given: 판매 이력 없음

        // When: 인기 상품 조회
        TopProductQuery query = new TopProductQuery("3d", 5);
        TopProductResult result = productService.getTopProducts(query);

        // Then: 빈 리스트
        assertThat(result.products()).isEmpty();
    }

    @Test
    @DisplayName("성능 테스트: 대량 주문 데이터에서 인기 상품 조회 - 1초 이내 완료")
    void performance_getTopProducts_WithLargeDataset_Within1Second() {
        // Given: 100개 주문 생성 (각 상품별 10개씩)
        Instant now = Instant.now(clock);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        for (int i = 0; i < 100; i++) {
            Product product = testProducts.get(i % 10);
            createConfirmedOrder(product, 1, twoDaysAgo);
        }

        // When: 인기 상품 조회 (시간 측정)
        long startTime = System.currentTimeMillis();
        TopProductQuery query = new TopProductQuery("3d", 5);
        TopProductResult result = productService.getTopProducts(query);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 1초 이내 완료
        assertThat(elapsedTime).isLessThan(1000);
        assertThat(result.products()).isNotEmpty();

        System.out.println("100개 주문 데이터에서 인기 상품 조회 소요 시간: " + elapsedTime + "ms");
    }

    @Test
    @DisplayName("성능 테스트: 1000개 주문 데이터에서 인기 상품 조회 - 2초 이내 완료")
    void performance_getTopProducts_With1000Orders_Within2Seconds() {
        // Given: 1000개 주문 생성
        Instant now = Instant.now(clock);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        for (int i = 0; i < 1000; i++) {
            Product product = testProducts.get(i % 10);
            createConfirmedOrder(product, 1, twoDaysAgo);
        }

        // When: 인기 상품 조회 (시간 측정)
        long startTime = System.currentTimeMillis();
        TopProductQuery query = new TopProductQuery("3d", 5);
        TopProductResult result = productService.getTopProducts(query);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 2초 이내 완료
        assertThat(elapsedTime).isLessThan(2000);
        assertThat(result.products()).hasSize(5);

        System.out.println("1000개 주문 데이터에서 인기 상품 조회 소요 시간: " + elapsedTime + "ms");
    }

    // === Helper Methods ===

    private void createConfirmedOrder(Product product, int quantity, Instant paidAt) {
        Order order = Order.create(testUser.getId(), product.getPrice() * quantity, paidAt.plus(30, ChronoUnit.MINUTES));
        order.confirm(product.getPrice() * quantity, paidAt);
        Order savedOrder = orderRepository.save(order);

        OrderItem orderItem = OrderItem.create(
                savedOrder.getId(),
                product.getId(),
                product.getName(),
                product.getPrice(),
                quantity
        );
        orderItemRepository.saveAll(List.of(orderItem));
    }

    private void createPendingOrder(Product product, int quantity, Instant createdAt) {
        Order order = Order.create(testUser.getId(), product.getPrice() * quantity, createdAt.plus(30, ChronoUnit.MINUTES));
        Order savedOrder = orderRepository.save(order);

        OrderItem orderItem = OrderItem.create(
                savedOrder.getId(),
                product.getId(),
                product.getName(),
                product.getPrice(),
                quantity
        );
        orderItemRepository.saveAll(List.of(orderItem));
    }

    private void createCancelledOrder(Product product, int quantity, Instant cancelledAt) {
        Order order = Order.create(testUser.getId(), product.getPrice() * quantity, cancelledAt.plus(30, ChronoUnit.MINUTES));
        order.cancel(cancelledAt);
        Order savedOrder = orderRepository.save(order);

        OrderItem orderItem = OrderItem.create(
                savedOrder.getId(),
                product.getId(),
                product.getName(),
                product.getPrice(),
                quantity
        );
        orderItemRepository.saveAll(List.of(orderItem));
    }
}
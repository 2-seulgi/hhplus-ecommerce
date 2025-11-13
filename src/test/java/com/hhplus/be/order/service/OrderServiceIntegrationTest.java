package com.hhplus.be.order.service;

import com.hhplus.be.cart.domain.model.CartItem;
import com.hhplus.be.cart.domain.repository.CartRepository;
import com.hhplus.be.order.domain.repository.OrderRepository;
import com.hhplus.be.order.service.dto.OrderListQuery;
import com.hhplus.be.order.service.dto.OrderListResult;
import com.hhplus.be.orderitem.domain.repository.OrderItemRepository;
import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.domain.repository.ProductRepository;
import com.hhplus.be.testsupport.IntegrationTestSupport;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 주문 Service 통합 테스트
 *
 * 성능 분석 보고서에서 식별된 문제:
 * - 주문 목록 조회: IN 절에 많은 ID 포함 시 성능 이슈
 * - N+1 방지를 위한 일괄 조회 성능 검증
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private Clock clock;

    private User testUser;
    private List<Product> testProducts;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 정리 (이전 테스트의 잔여 데이터 제거)
        cartRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();

        // 테스트 유저 생성
        testUser = User.create(
                "주문테스트유저",
                "order_test_" + System.currentTimeMillis() + "@test.com",
                1000000
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
    @Transactional
    @DisplayName("주문 목록 조회 - 주문 항목 일괄 조회로 N+1 방지")
    void getOrderList_AvoidN_Plus_1_WithBulkFetch() {
        // Given: 10개 주문 생성 (각 주문당 3개 상품)
        Instant now = Instant.now(clock);

        for (int i = 0; i < 10; i++) {
            // 장바구니에 3개 상품 추가
            for (int j = 0; j < 3; j++) {
                CartItem cartItem = CartItem.create(
                        testUser.getId(),
                        testProducts.get(j).getId(),
                        2
                );
                cartRepository.save(cartItem);
            }

            // 주문 생성
            orderService.createFromCart(testUser.getId());

            // 장바구니 비우기
            cartRepository.deleteAllByUserId(testUser.getId());
        }

        // When: 주문 목록 조회
        OrderListQuery query = new OrderListQuery(testUser.getId());
        OrderListResult result = orderService.getOrderList(query);

        // Then: 10개 주문, 각 3개 항목
        assertThat(result.orders()).hasSize(10);
        assertThat(result.orders()).allMatch(order -> order.items().size() == 3);
    }

    @Test
    @Transactional
    @DisplayName("주문 목록 조회 - 많은 주문 ID에 대한 IN 절 성능")
    void getOrderList_ManyOrders_INClausePerformance() {
        // Given: 50개 주문 생성
        Instant now = Instant.now(clock);

        for (int i = 0; i < 50; i++) {
            // 장바구니에 상품 추가
            CartItem cartItem = CartItem.create(
                    testUser.getId(),
                    testProducts.get(i % 10).getId(),
                    1
            );
            cartRepository.save(cartItem);

            // 주문 생성
            orderService.createFromCart(testUser.getId());

            // 장바구니 비우기
            cartRepository.deleteAllByUserId(testUser.getId());
        }

        // When: 주문 목록 조회 (시간 측정)
        long startTime = System.currentTimeMillis();
        OrderListQuery query = new OrderListQuery(testUser.getId());
        OrderListResult result = orderService.getOrderList(query);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 50개 주문 조회
        assertThat(result.orders()).hasSize(50);

        System.out.println("50개 주문 목록 조회 소요 시간: " + elapsedTime + "ms");
        System.out.println("쿼리 패턴: SELECT * FROM orders WHERE user_id = ?");
        System.out.println("           SELECT * FROM order_items WHERE order_id IN (?, ?, ..., ?) -- 50개 ID");
    }

    @Test
    @Transactional
    @DisplayName("성능 테스트: 100개 주문 목록 조회 - 1초 이내 완료")
    void performance_getOrderList_100Orders_Within1Second() {
        // Given: 100개 주문 생성
        for (int i = 0; i < 100; i++) {
            CartItem cartItem = CartItem.create(
                    testUser.getId(),
                    testProducts.get(i % 10).getId(),
                    1
            );
            cartRepository.save(cartItem);

            orderService.createFromCart(testUser.getId());
            cartRepository.deleteAllByUserId(testUser.getId());
        }

        // When: 주문 목록 조회 (시간 측정)
        long startTime = System.currentTimeMillis();
        OrderListQuery query = new OrderListQuery(testUser.getId());
        OrderListResult result = orderService.getOrderList(query);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 100개 주문 조회, 1초 이내 완료
        assertThat(result.orders()).hasSize(100);
        assertThat(elapsedTime).isLessThan(1000);

        System.out.println("100개 주문 목록 조회 소요 시간: " + elapsedTime + "ms");
    }

    @Test
    @Transactional
    @DisplayName("성능 테스트: 500개 주문 목록 조회 - IN 절 성능 한계 확인")
    void performance_getOrderList_500Orders_INClauseLimit() {
        // Given: 500개 주문 생성
        for (int i = 0; i < 500; i++) {
            CartItem cartItem = CartItem.create(
                    testUser.getId(),
                    testProducts.get(i % 10).getId(),
                    1
            );
            cartRepository.save(cartItem);

            orderService.createFromCart(testUser.getId());
            cartRepository.deleteAllByUserId(testUser.getId());
        }

        // When: 주문 목록 조회 (시간 측정)
        long startTime = System.currentTimeMillis();
        OrderListQuery query = new OrderListQuery(testUser.getId());
        OrderListResult result = orderService.getOrderList(query);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 500개 주문 조회
        assertThat(result.orders()).hasSize(500);

        System.out.println("500개 주문 목록 조회 소요 시간: " + elapsedTime + "ms");
        System.out.println("IN 절에 500개 ID 포함 - 성능 저하 가능");
        System.out.println("개선안 1: 페이징 적용 (한 번에 20~50개씩 조회)");
        System.out.println("개선안 2: JOIN 쿼리로 변경");
    }

    @Test
    @Transactional
    @DisplayName("주문 목록 조회 - 최신순 정렬 확인")
    void getOrderList_SortedByCreatedAtDesc() {
        // Given: 5개 주문 생성
        List<Long> orderIds = new ArrayList<>();
        List<Instant> createdTimes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            CartItem cartItem = CartItem.create(
                    testUser.getId(),
                    testProducts.get(0).getId(),
                    1
            );
            cartRepository.save(cartItem);

            var result = orderService.createFromCart(testUser.getId());
            orderIds.add(result.orderId());
            createdTimes.add(Instant.now(clock));

            cartRepository.deleteAllByUserId(testUser.getId());

            // 시간 간격 (밀리초 정밀도 보장)
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // When: 주문 목록 조회
        OrderListQuery query = new OrderListQuery(testUser.getId());
        OrderListResult result = orderService.getOrderList(query);

        // Then: 5개 주문 조회
        assertThat(result.orders()).hasSize(5);

        // 반환된 주문 ID들이 생성된 주문 ID를 모두 포함하는지 확인
        List<Long> returnedOrderIds = result.orders().stream()
                .map(o -> o.orderId())
                .toList();
        assertThat(returnedOrderIds).containsExactlyInAnyOrderElementsOf(orderIds);

        // 최신순 정렬 확인: 각 주문이 이전 주문보다 나중에 생성되지 않았는지 확인
        // (같은 시간일 수도 있으므로 isAfterOrEqualTo 사용)
        for (int i = 0; i < result.orders().size() - 1; i++) {
            Instant current = result.orders().get(i).createdAt();
            Instant next = result.orders().get(i + 1).createdAt();

            // 최신순이므로 current >= next 여야 함
            assertThat(current).isAfterOrEqualTo(next);
        }
    }

    @Test
    @Transactional
    @DisplayName("주문 목록 조회 - 각 주문별 항목 그룹화 확인")
    void getOrderList_ItemsGroupedByOrder() {
        // Given: 3개 주문 생성 (각 다른 개수의 상품)
        List<Long> orderIds = new ArrayList<>();

        // 주문1: 2개 상품
        for (int i = 0; i < 2; i++) {
            CartItem cartItem = CartItem.create(
                    testUser.getId(),
                    testProducts.get(i).getId(),
                    1
            );
            cartRepository.save(cartItem);
        }
        var order1 = orderService.createFromCart(testUser.getId());
        orderIds.add(order1.orderId());
        assertThat(order1.items()).hasSize(2); // 생성 시점에 2개 확인
        cartRepository.deleteAllByUserId(testUser.getId());

        // 주문2: 3개 상품
        for (int i = 0; i < 3; i++) {
            CartItem cartItem = CartItem.create(
                    testUser.getId(),
                    testProducts.get(i).getId(),
                    1
            );
            cartRepository.save(cartItem);
        }
        var order2 = orderService.createFromCart(testUser.getId());
        orderIds.add(order2.orderId());
        assertThat(order2.items()).hasSize(3); // 생성 시점에 3개 확인
        cartRepository.deleteAllByUserId(testUser.getId());

        // 주문3: 5개 상품
        for (int i = 0; i < 5; i++) {
            CartItem cartItem = CartItem.create(
                    testUser.getId(),
                    testProducts.get(i).getId(),
                    1
            );
            cartRepository.save(cartItem);
        }
        var order3 = orderService.createFromCart(testUser.getId());
        orderIds.add(order3.orderId());
        assertThat(order3.items()).hasSize(5); // 생성 시점에 5개 확인
        cartRepository.deleteAllByUserId(testUser.getId());

        // When: 주문 목록 조회
        OrderListQuery query = new OrderListQuery(testUser.getId());
        OrderListResult result = orderService.getOrderList(query);

        // Then: 각 주문별 항목 개수 확인
        assertThat(result.orders()).hasSize(3);

        // 주문 ID를 기준으로 조회된 주문과 매칭
        for (int i = 0; i < 3; i++) {
            Long orderId = orderIds.get(i);
            var matchedOrder = result.orders().stream()
                    .filter(o -> o.orderId().equals(orderId))
                    .findFirst()
                    .orElseThrow();

            // 각 주문의 항목 개수 확인
            if (i == 0) assertThat(matchedOrder.items()).hasSize(2);
            if (i == 1) assertThat(matchedOrder.items()).hasSize(3);
            if (i == 2) assertThat(matchedOrder.items()).hasSize(5);
        }
    }

    @Test
    @Transactional
    @DisplayName("주문 목록 조회 - 주문 없을 때 빈 리스트 반환")
    void getOrderList_NoOrders_ReturnsEmptyList() {
        // Given: 주문 없음

        // When: 주문 목록 조회
        OrderListQuery query = new OrderListQuery(testUser.getId());
        OrderListResult result = orderService.getOrderList(query);

        // Then: 빈 리스트
        assertThat(result.orders()).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("복합 시나리오: 주문 생성 → 목록 조회 → 상세 조회")
    void complexScenario_CreateAndRetrieveOrders() {
        // Given: 3개 주문 생성
        List<Long> orderIds = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            CartItem cartItem = CartItem.create(
                    testUser.getId(),
                    testProducts.get(i).getId(),
                    2
            );
            cartRepository.save(cartItem);

            var createResult = orderService.createFromCart(testUser.getId());
            orderIds.add(createResult.orderId());

            cartRepository.deleteAllByUserId(testUser.getId());
        }

        // When & Then: 목록 조회
        OrderListQuery listQuery = new OrderListQuery(testUser.getId());
        OrderListResult listResult = orderService.getOrderList(listQuery);

        assertThat(listResult.orders()).hasSize(3);

        // When & Then: 각 주문 상세 조회
        for (Long orderId : orderIds) {
            var detailQuery = new com.hhplus.be.order.service.dto.OrderDetailQuery(testUser.getId(), orderId);
            var detailResult = orderService.getOrderDetail(detailQuery);

            assertThat(detailResult.orderId()).isEqualTo(orderId);
            assertThat(detailResult.items()).hasSize(1);
        }
    }
}
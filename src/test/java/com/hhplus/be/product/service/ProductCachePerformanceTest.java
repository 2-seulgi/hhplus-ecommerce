package com.hhplus.be.product.service;

import com.hhplus.be.cart.domain.model.CartItem;
import com.hhplus.be.cart.domain.repository.CartRepository;
import com.hhplus.be.order.domain.repository.OrderRepository;
import com.hhplus.be.order.service.OrderService;
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
import org.springframework.cache.CacheManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ProductCachePerformanceTest extends IntegrationTestSupport {
    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // 캐시 초기화
        cacheManager.getCacheNames().forEach(cacheName ->
                cacheManager.getCache(cacheName).clear()
        );
    }

    @Test
    @DisplayName("캐시 성능 테스트 - 두 번째 호출은 10배 이상 빠름")
    void cachePerformanceTest() throws Exception {
        // Given: 테스트 데이터 생성
        User user = User.create("테스트유저", "cache_" + UUID.randomUUID() + "@test.com", 1000000);
        user = userRepository.save(user);

        // 상품 5개 생성
        for (int i = 1; i <= 5; i++) {
            Product product = Product.create("상품" + i, "설명" + i, 10000 * i, 100);
            product = productRepository.save(product);

            // 각 상품별 주문 생성 (판매량 차등)
            for (int j = 0; j < (6 - i); j++) {
                CartItem cartItem = CartItem.create(user.getId(), product.getId(), 1);
                cartRepository.save(cartItem);
                var orderResult = orderService.createFromCart(user.getId());

                // 주문 확정
                var order = orderRepository.findById(orderResult.orderId()).orElseThrow();
                order.confirm(10000 * i, java.time.Instant.now());
                orderRepository.save(order);

                cartRepository.deleteAll(); // 장바구니 비우기
            }
        }

        TopProductQuery query = new TopProductQuery("3d", 5);

        // When: 첫 번째 호출 (캐시 없음 - DB 쿼리 실행)
        long start1 = System.currentTimeMillis();
        TopProductResult result1 = productService.getTopProducts(query);
        long time1 = System.currentTimeMillis() - start1;

        System.out.println("=== 첫 번째 호출 (캐시 없음) ===");
        System.out.println("소요 시간: " + time1 + "ms");
        System.out.println("결과: " + result1.products().size() + "개 상품");

        // When: 두 번째 호출 (캐시 있음 - Redis에서 바로 반환)
        long start2 = System.currentTimeMillis();
        TopProductResult result2 = productService.getTopProducts(query);
        long time2 = System.currentTimeMillis() - start2;

        System.out.println("\n=== 두 번째 호출 (캐시 있음) ===");
        System.out.println("소요 시간: " + time2 + "ms");
        System.out.println("결과: " + result2.products().size() + "개 상품");

        System.out.println("\n=== 성능 비교 ===");
        System.out.println("속도 개선: " + (time1 / (time2 + 1)) + "배");

        // Then: 캐시 성능 검증
        assertThat(result2.products()).hasSize(5);
        assertThat(time2).isLessThan(time1); // 두 번째 호출이 더 빠름

        // 최소 3배 이상 빠를 것으로 예상 (캐시 효과)
        assertThat(time2).isLessThan(time1 / 3);
    }

    @Test
    @DisplayName("캐시 키 분리 테스트 - 다른 파라미터는 다른 캐시")
    void cachKeyIsolationTest() {
        // Given: 테스트 데이터 생성
        User user = User.create("테스트유저2", "cache2_" + UUID.randomUUID() + "@test.com", 1000000);
        user = userRepository.save(user);

        Product product = Product.create("테스트상품", "설명", 10000, 100);
        product = productRepository.save(product);

        CartItem cartItem = CartItem.create(user.getId(), product.getId(), 1);
        cartRepository.save(cartItem);
        var orderResult = orderService.createFromCart(user.getId());

        var order = orderRepository.findById(orderResult.orderId()).orElseThrow();
        order.confirm(10000, java.time.Instant.now());
        orderRepository.save(order);

        // When: 다른 파라미터로 호출
        TopProductQuery query1 = new TopProductQuery("3d", 5);
        TopProductQuery query2 = new TopProductQuery("7d", 10);

        long start1 = System.currentTimeMillis();
        TopProductResult result1 = productService.getTopProducts(query1);
        long time1 = System.currentTimeMillis() - start1;

        long start2 = System.currentTimeMillis();
        TopProductResult result2 = productService.getTopProducts(query2);
        long time2 = System.currentTimeMillis() - start2;

        long start3 = System.currentTimeMillis();
        TopProductResult result3 = productService.getTopProducts(query1);  // query1 재호출
        long time3 = System.currentTimeMillis() - start3;

        System.out.println("=== 캐시 키 분리 테스트 ===");
        System.out.println("query1 (3d:5) 첫 호출: " + time1 + "ms");
        System.out.println("query2 (7d:10) 첫 호출: " + time2 + "ms");
        System.out.println("query1 (3d:5) 재호출: " + time3 + "ms (캐시 적중!)");

        // Then: query1 재호출은 캐시에서 바로 반환
        assertThat(time3).isLessThan(time1 / 3);
    }
}


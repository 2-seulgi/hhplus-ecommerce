package com.hhplus.be.order.usecase;

import com.hhplus.be.cart.domain.model.CartItem;
import com.hhplus.be.cart.domain.repository.CartRepository;
import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.coupon.domain.model.Coupon;
import com.hhplus.be.coupon.domain.model.DiscountType;
import com.hhplus.be.coupon.domain.repository.CouponRepository;
import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.domain.model.OrderStatus;
import com.hhplus.be.order.domain.repository.OrderRepository;
import com.hhplus.be.order.service.OrderService;
import com.hhplus.be.order.service.dto.CreateOrderResult;
import com.hhplus.be.order.service.dto.PaymentCommand;
import com.hhplus.be.order.service.dto.PaymentResult;
import com.hhplus.be.orderitem.domain.repository.OrderItemRepository;
import com.hhplus.be.point.domain.repository.PointRepository;
import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.domain.repository.ProductRepository;
import com.hhplus.be.testsupport.IntegrationTestSupport;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.user.domain.repository.UserRepository;
import com.hhplus.be.usercoupon.domain.model.UserCoupon;
import com.hhplus.be.usercoupon.domain.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 결제 UseCase 통합 테스트
 *
 * 전체 결제 플로우 검증:
 * - 주문 검증 → 쿠폰 할인 → 재고 차감 → 포인트 차감 → 주문 확정
 * - 트랜잭션 롤백 확인
 * - 동시 결제 처리
 */
@SpringBootTest
@ActiveProfiles("test")
class ProcessPaymentUseCaseIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

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
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private Clock clock;

    private User testUser;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        pointRepository.deleteAll();
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트 유저 생성 (잔액 100만원)
        testUser = User.create(
                "결제테스트유저",
                "payment_test_" + System.currentTimeMillis() + "@test.com",
                1000000
        );
        testUser = userRepository.save(testUser);

        // 테스트 상품 생성 (재고 100개)
        testProduct = Product.create(
                "테스트상품",
                "테스트 상품 설명",
                50000,
                100
        );
        testProduct = productRepository.save(testProduct);
    }

    @Test
    @DisplayName("결제 성공 - 전체 플로우 (주문 생성 → 결제 → 주문 확정)")
    void processPayment_FullFlow_Success() {
        // Given: 장바구니에 상품 추가 → 주문 생성
        CartItem cartItem = CartItem.create(testUser.getId(), testProduct.getId(), 2);
        cartRepository.save(cartItem);

        CreateOrderResult orderResult = orderService.createFromCart(testUser.getId());
        Long orderId = orderResult.orderId();

        // 초기 상태 확인
        int initialStock = testProduct.getStock();
        int initialBalance = testUser.getBalance();

        // When: 결제 처리
        PaymentCommand command = new PaymentCommand(testUser.getId(), orderId, null);
        PaymentResult result = processPaymentUseCase.execute(command);

        // Then: 결제 성공
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.finalAmount()).isEqualTo(100000); // 50,000 × 2

        // 주문 상태 확인
        Order confirmedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(confirmedOrder.getPaidAt()).isNotNull();

        // 재고 차감 확인
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(initialStock - 2);

        // 포인트 차감 확인
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getBalance()).isEqualTo(initialBalance - 100000);

        // 포인트 히스토리 확인
        assertThat(pointRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId())).hasSize(1);
    }

    @Test
    @DisplayName("쿠폰 적용 결제 - 할인 금액 차감")
    void processPayment_WithCoupon_ApplyDiscount() {
        // Given: 쿠폰 생성 및 발급
        Instant now = Instant.now(clock);
        Coupon coupon = Coupon.create(
                "DISCOUNT_" + System.currentTimeMillis(),
                "10% 할인 쿠폰",
                DiscountType.PERCENTAGE,
                10,
                100,
                0,
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS)
        );
        Coupon savedCoupon = couponRepository.save(coupon);

        UserCoupon userCoupon = UserCoupon.create(testUser.getId(), savedCoupon.getId(), now);
        UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);

        // 주문 생성
        CartItem cartItem = CartItem.create(testUser.getId(), testProduct.getId(), 2);
        cartRepository.save(cartItem);
        CreateOrderResult orderResult = orderService.createFromCart(testUser.getId());
        Long orderId = orderResult.orderId();

        int initialBalance = testUser.getBalance();

        // When: 쿠폰 코드로 결제
        PaymentCommand command = new PaymentCommand(testUser.getId(), orderId, savedCoupon.getCode());
        PaymentResult result = processPaymentUseCase.execute(command);

        // Then: 할인 적용 (100,000 - 10% = 90,000)
        assertThat(result.discountAmount()).isEqualTo(10000);
        assertThat(result.finalAmount()).isEqualTo(90000);

        // 포인트는 할인된 금액만큼만 차감
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getBalance()).isEqualTo(initialBalance - 90000);

        // 쿠폰 사용 처리 확인
        UserCoupon usedCoupon = userCouponRepository.findById(savedUserCoupon.getId()).orElseThrow();
        assertThat(usedCoupon.isUsed()).isTrue();
    }

    @Test
    @DisplayName("포인트 부족 시 결제 실패 - 트랜잭션 롤백")
    void processPayment_InsufficientBalance_RollbackTransaction() {
        // Given: 잔액 부족한 유저
        User poorUser = User.create(
                "가난한유저",
                "poor_" + System.currentTimeMillis() + "@test.com",
                10000  // 잔액 1만원
        );
        poorUser = userRepository.save(poorUser);

        // 10만원짜리 주문 생성
        CartItem cartItem = CartItem.create(poorUser.getId(), testProduct.getId(), 2);
        cartRepository.save(cartItem);
        CreateOrderResult orderResult = orderService.createFromCart(poorUser.getId());
        Long orderId = orderResult.orderId();

        int initialStock = testProduct.getStock();

        // When & Then: 결제 실패
        PaymentCommand command = new PaymentCommand(poorUser.getId(), orderId, null);
        assertThatThrownBy(() -> processPaymentUseCase.execute(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("부족");

        // 재고는 차감되지 않아야 함 (트랜잭션 롤백)
        Product unchangedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(unchangedProduct.getStock()).isEqualTo(initialStock);

        // 주문 상태는 PENDING 유지
        Order unchangedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(unchangedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("재고 부족 시 결제 실패 - 트랜잭션 롤백")
    void processPayment_OutOfStock_RollbackTransaction() {
        // Given: 재고 부족 상품
        Product lowStockProduct = Product.create(
                "품절임박상품",
                "재고 1개",
                50000,
                1  // 재고 1개
        );
        lowStockProduct = productRepository.save(lowStockProduct);

        // 2개 주문
        CartItem cartItem = CartItem.create(testUser.getId(), lowStockProduct.getId(), 2);
        cartRepository.save(cartItem);
        CreateOrderResult orderResult = orderService.createFromCart(testUser.getId());
        Long orderId = orderResult.orderId();

        int initialBalance = testUser.getBalance();

        // When & Then: 결제 실패
        PaymentCommand command = new PaymentCommand(testUser.getId(), orderId, null);
        assertThatThrownBy(() -> processPaymentUseCase.execute(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("재고");

        // 포인트는 차감되지 않아야 함 (트랜잭션 롤백)
        User unchangedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(unchangedUser.getBalance()).isEqualTo(initialBalance);

        // 주문 상태는 PENDING 유지
        Order unchangedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(unchangedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("동시 결제 처리 - 동일 상품에 대한 재고 차감 동시성 제어")
    void concurrency_ProcessPayment_StockDeduction() throws InterruptedException {
        // Given: 재고 10개 상품
        Product limitedProduct = Product.create(
                "한정상품",
                "재고 10개",
                10000,
                10
        );
        limitedProduct = productRepository.save(limitedProduct);

        // 20명의 유저 생성 (각 100만원)
        List<User> users = new ArrayList<>();
        List<Long> orderIds = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            User user = User.create(
                    "유저" + i,
                    "user" + i + "_" + System.currentTimeMillis() + "@test.com",
                    1000000
            );
            users.add(userRepository.save(user));

            // 각 유저별 주문 생성 (1개씩)
            CartItem cartItem = CartItem.create(user.getId(), limitedProduct.getId(), 1);
            cartRepository.save(cartItem);
            CreateOrderResult orderResult = orderService.createFromCart(user.getId());
            orderIds.add(orderResult.orderId());
            cartRepository.deleteAllByUserId(user.getId());
        }

        // When: 20명이 동시에 결제 시도
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(20);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < 20; i++) {
            int index = i;
            executorService.submit(() -> {
                try {
                    PaymentCommand command = new PaymentCommand(
                            users.get(index).getId(),
                            orderIds.get(index),
                            null
                    );
                    processPaymentUseCase.execute(command);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getMessage().contains("재고")) {
                        failCount.incrementAndGet();
                    } else {
                        throw e;
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 10명 성공, 10명 실패
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(10);

        // 최종 재고 0개
        Product finalProduct = productRepository.findById(limitedProduct.getId()).orElseThrow();
        assertThat(finalProduct.getStock()).isEqualTo(0);

        System.out.println("성공: " + successCount.get() + ", 실패: " + failCount.get());
    }

    @Test
    @DisplayName("성능 테스트: 100개 주문 동시 결제 - 10초 이내 완료")
    void performance_ProcessPayment_100Orders_Within10Seconds() throws InterruptedException {
        // Given: 충분한 재고
        Product bulkProduct = Product.create(
                "대량상품",
                "재고 1000개",
                10000,
                1000
        );
        bulkProduct = productRepository.save(bulkProduct);

        // 100명 유저 + 주문 생성
        List<User> users = new ArrayList<>();
        List<Long> orderIds = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            User user = User.create(
                    "대량유저" + i,
                    "bulk" + i + "_" + System.currentTimeMillis() + "@test.com",
                    1000000
            );
            users.add(userRepository.save(user));

            CartItem cartItem = CartItem.create(user.getId(), bulkProduct.getId(), 1);
            cartRepository.save(cartItem);
            CreateOrderResult orderResult = orderService.createFromCart(user.getId());
            orderIds.add(orderResult.orderId());
            cartRepository.deleteAllByUserId(user.getId());
        }

        // When: 100명 동시 결제 (시간 측정)
        long startTime = System.currentTimeMillis();

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(100);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 100; i++) {
            int index = i;
            executorService.submit(() -> {
                try {
                    PaymentCommand command = new PaymentCommand(
                            users.get(index).getId(),
                            orderIds.get(index),
                            null
                    );
                    processPaymentUseCase.execute(command);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(20, TimeUnit.SECONDS);
        executorService.shutdown();

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 100개 모두 성공, 10초 이내
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(elapsedTime).isLessThan(10000);

        System.out.println("100개 주문 동시 결제 소요 시간: " + elapsedTime + "ms");
    }
}
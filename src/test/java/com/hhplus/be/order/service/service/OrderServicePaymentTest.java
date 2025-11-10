package com.hhplus.be.order.service.service;

import com.hhplus.be.order.domain.Order;
import com.hhplus.be.order.domain.OrderStatus;
import com.hhplus.be.order.infrastructure.OrderRepository;
import com.hhplus.be.order.service.OrderService;
import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.InsufficientBalanceException;
import com.hhplus.be.coupon.domain.Coupon;
import com.hhplus.be.coupon.domain.DiscountType;
import com.hhplus.be.coupon.infrastructure.CouponRepository;
import com.hhplus.be.order.service.dto.PaymentCommand;
import com.hhplus.be.order.service.dto.PaymentResult;
import com.hhplus.be.orderdiscount.infrastructure.OrderDiscountRepository;
import com.hhplus.be.orderitem.domain.OrderItem;
import com.hhplus.be.orderitem.infrastructure.OrderItemRepository;
import com.hhplus.be.point.domain.Point;
import com.hhplus.be.point.domain.PointType;
import com.hhplus.be.point.infrastructure.PointRepository;
import com.hhplus.be.product.domain.Product;
import com.hhplus.be.product.infrastructure.ProductRepository;
import com.hhplus.be.user.domain.User;
import com.hhplus.be.user.infrastructure.UserRepository;
import com.hhplus.be.usercoupon.UserCoupon;
import com.hhplus.be.usercoupon.infrastructure.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServicePaymentTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PointRepository pointRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private UserCouponRepository userCouponRepository;
    @Mock private OrderDiscountRepository orderDiscountRepository;
    @Mock private Clock clock;

    @InjectMocks
    private OrderService orderService;

    private Instant fixedNow;

    @BeforeEach
    void setUp() {
        // 시간을 고정 (테스트 일관성)
        fixedNow = Instant.parse("2025-11-06T10:00:00Z");
        when(clock.instant()).thenReturn(fixedNow);
    }

    @Test
    @DisplayName("쿠폰 없이 결제 성공 - 주문 CONFIRMED, 재고/포인트 차감")
    void processPayment_withoutCoupon_success() {
        // Given: 테스트 데이터 준비
        Long userId = 1L;
        Long orderId = 100L;
        int totalAmount = 213000; // 무선이어폰(89000*2) + 보조배터리(35000*1) = 213000

        // 1. PENDING 상태 주문 (만료 전)
        Instant expiresAt = fixedNow.plusSeconds(1800); // 30분 후
        Order pendingOrder = Order.create(userId, totalAmount, expiresAt);
        pendingOrder.assignId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));

        // 2. 주문 항목들 (무선이어폰 2개, 보조배터리 1개)
        List<OrderItem> orderItems = List.of(
            createOrderItem(1L, orderId, 1L, "무선 이어폰", 89000, 2),
            createOrderItem(2L, orderId, 2L, "보조배터리", 35000, 1)
        );
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems);

        // 3. 상품들 (재고 충분)
        Product product1 = Product.create("무선 이어폰", "고음질", 89000, 100);
        product1.assignId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product1));

        Product product2 = Product.create("보조배터리", "20000mAh", 35000, 50);
        product2.assignId(2L);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product2));

        // 4. 사용자 (포인트 충분: 300,000원)
        User user = User.createWithId(userId, "홍길동", "hong@test.com", 300000);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // 5. 포인트 히스토리 저장 시 그대로 반환
        when(pointRepository.save(any(Point.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: 결제 실행 (쿠폰 없음)
        PaymentCommand command = new PaymentCommand(userId, orderId, null);
        PaymentResult result = orderService.processPayment(command);

        // Then: 검증
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        assertThat(product1.getStock()).isEqualTo(98);  // 100 - 2
        assertThat(product2.getStock()).isEqualTo(49);  // 50 - 1

        assertThat(user.getBalance()).isEqualTo(87000);  // 300000 - 213000

        verify(pointRepository).save(argThat(point ->
             point.getPointType() == PointType.USE &&
             point.getAmount() == 213000 &&
             point.getBalanceAfter() == 87000
         ));

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.totalAmount()).isEqualTo(213000);
        assertThat(result.discountAmount()).isEqualTo(0);  // 쿠폰 없음
        assertThat(result.finalAmount()).isEqualTo(213000);
        assertThat(result.remainingBalance()).isEqualTo(87000);
        assertThat(result.paidAt()).isEqualTo(fixedNow);
    }

    @Test
    @DisplayName("쿠폰 적용하여 결제 성공 - 할인 금액 차감")
    void processPayment_withCoupon_success() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        int totalAmount = 30000;
        String couponCode = "WELCOME10";

        // 주문
        Instant expiresAt = fixedNow.plusSeconds(1800);
        Order pendingOrder = Order.create(userId, totalAmount, expiresAt);
        pendingOrder.assignId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));

        // 주문 항목
        List<OrderItem> orderItems = List.of(
                createOrderItem(1L, orderId, 1L, "상품", 30000, 1)
        );
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems);

        // 상품
        Product product = Product.create("상품", "설명", 30000, 10);
        product.assignId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // 쿠폰 (5000원 할인)
        Coupon coupon = Coupon.create(
                couponCode, "신규회원 쿠폰", DiscountType.FIXED, 5000, 100, 50,
                fixedNow.minusSeconds(3600), fixedNow.plusSeconds(86400),
                fixedNow.minusSeconds(3600), fixedNow.plusSeconds(86400)
        );
        when(couponRepository.findByCode(couponCode)).thenReturn(Optional.of(coupon));

        // 사용자 쿠폰
        UserCoupon userCoupon = UserCoupon.create(userId, coupon.getId(), fixedNow.minusSeconds(3600));
        when(userCouponRepository.findByUserIdAndCouponId(userId, coupon.getId()))
                .thenReturn(Optional.of(userCoupon));

        // 사용자 (포인트 충분)
        User user = User.createWithId(userId, "홍길동", "hong@test.com", 100000);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        PaymentCommand command = new PaymentCommand(userId, orderId, couponCode);
        PaymentResult result = orderService.processPayment(command);

        // Then
        assertThat(result.totalAmount()).isEqualTo(30000);
        assertThat(result.discountAmount()).isEqualTo(5000);
        assertThat(result.finalAmount()).isEqualTo(25000);  // 30000 - 5000
        assertThat(user.getBalance()).isEqualTo(75000);  // 100000 - 25000
        assertThat(userCoupon.isUsed()).isTrue();
        verify(orderDiscountRepository).save(any());
    }

    @Test
    @DisplayName("주문 만료된 경우 결제 실패")
    void processPayment_expiredOrder_fail() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;

        // 만료된 주문
        Instant expiresAt = fixedNow.minusSeconds(1);  // 이미 만료
        Order expiredOrder = Order.create(userId, 30000, expiresAt);
        expiredOrder.assignId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(expiredOrder));

        // When & Then
        PaymentCommand command = new PaymentCommand(userId, orderId, null);
        assertThatThrownBy(() -> orderService.processPayment(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("만료");
    }

    @Test
    @DisplayName("재고 부족 시 결제 실패")
    void processPayment_outOfStock_fail() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;

        Order pendingOrder = Order.create(userId, 30000, fixedNow.plusSeconds(1800));
        pendingOrder.assignId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));

        List<OrderItem> orderItems = List.of(
                createOrderItem(1L, orderId, 1L, "상품", 30000, 5)
        );
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems);

        // 재고 부족 (주문 수량 5개 > 재고 2개)
        Product product = Product.create("상품", "설명", 30000, 2);
        product.assignId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // When & Then
        PaymentCommand command = new PaymentCommand(userId, orderId, null);
        assertThatThrownBy(() -> orderService.processPayment(command))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("포인트 부족 시 결제 실패")
    void processPayment_insufficientBalance_fail() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        int totalAmount = 30000;

        Order pendingOrder = Order.create(userId, totalAmount, fixedNow.plusSeconds(1800));
        pendingOrder.assignId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));

        List<OrderItem> orderItems = List.of(
                createOrderItem(1L, orderId, 1L, "상품", 30000, 1)
        );
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems);

        Product product = Product.create("상품", "설명", 30000, 10);
        product.assignId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // 포인트 부족 (필요: 30000, 보유: 10000)
        User user = User.createWithId(userId, "홍길동", "hong@test.com", 10000);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When & Then
        PaymentCommand command = new PaymentCommand(userId, orderId, null);
        assertThatThrownBy(() -> orderService.processPayment(command))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    @DisplayName("쿠폰이 이미 사용된 경우 결제 실패")
    void processPayment_couponAlreadyUsed_fail() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        String couponCode = "WELCOME10";

        Order pendingOrder = Order.create(userId, 30000, fixedNow.plusSeconds(1800));
        pendingOrder.assignId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));

        List<OrderItem> orderItems = List.of(
                createOrderItem(1L, orderId, 1L, "상품", 30000, 1)
        );
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems);

        Coupon coupon = Coupon.create(
                couponCode, "신규회원 쿠폰", DiscountType.FIXED, 5000, 100, 50,
                fixedNow.minusSeconds(3600), fixedNow.plusSeconds(86400),
                fixedNow.minusSeconds(3600), fixedNow.plusSeconds(86400)
        );
        when(couponRepository.findByCode(couponCode)).thenReturn(Optional.of(coupon));

        // 이미 사용된 쿠폰
        UserCoupon userCoupon = UserCoupon.create(userId, coupon.getId(), fixedNow.minusSeconds(3600));
        userCoupon.use();  // 이미 사용됨
        when(userCouponRepository.findByUserIdAndCouponId(userId, coupon.getId()))
                .thenReturn(Optional.of(userCoupon));

        // When & Then
        PaymentCommand command = new PaymentCommand(userId, orderId, couponCode);
        assertThatThrownBy(() -> orderService.processPayment(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 사용");
    }

    // 헬퍼 메서드
    private OrderItem createOrderItem(Long id, Long orderId, Long productId,
                                      String productName, int unitPrice, int quantity) {
        OrderItem item = OrderItem.create(orderId, productId, productName, unitPrice, quantity);
        item.assignId(id);
        return item;
    }
}
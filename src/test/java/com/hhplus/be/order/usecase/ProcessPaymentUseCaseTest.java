package com.hhplus.be.order.usecase;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.InsufficientBalanceException;
import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.domain.model.OrderStatus;
import com.hhplus.be.order.service.OrderService;
import com.hhplus.be.order.service.dto.PaymentCommand;
import com.hhplus.be.order.service.dto.PaymentResult;
import com.hhplus.be.orderitem.domain.OrderItem;
import com.hhplus.be.point.service.PointService;
import com.hhplus.be.product.service.ProductService;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.coupon.service.CouponService;
import com.hhplus.be.coupon.service.dto.DiscountCalculationResult;
import com.hhplus.be.coupon.service.dto.ValidateDiscountCommand;
import com.hhplus.be.usercoupon.service.dto.DiscountCalculation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessPaymentUseCaseTest {

    @Mock private OrderService orderService;
    @Mock private CouponService couponService;
    @Mock private ProductService productService;
    @Mock private PointService pointService;

    private ProcessPaymentUseCase processPaymentUseCase;

    private Instant fixedNow;
    private Clock clock;

    @BeforeEach
    void setUp() {
        fixedNow = Instant.parse("2025-11-06T10:00:00Z");
        clock = Clock.fixed(fixedNow, ZoneId.systemDefault());
        processPaymentUseCase = new ProcessPaymentUseCase(
                orderService,
                couponService,
                productService,
                pointService,
                clock
        );
    }

    @Test
    @DisplayName("쿠폰 없이 결제 성공 - 주문 CONFIRMED, 재고/포인트 차감")
    void processPayment_withoutCoupon_success() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        int totalAmount = 50000;
        int userBalance = 100000;

        PaymentCommand command = new PaymentCommand(userId, orderId, null);

        Order order = Order.create(userId, totalAmount, fixedNow.plusSeconds(1800));
        order.assignId(orderId);

        List<OrderItem> items = List.of(
                OrderItem.create(orderId, 1L, "상품A", 10000, 2),
                OrderItem.create(orderId, 2L, "상품B", 30000, 1)
        );

        User user = User.create(userId, "홍길동", "hong@test.com", userBalance);

        when(orderService.validateForPayment(userId, orderId, fixedNow)).thenReturn(order);
        when(orderService.getOrderItems(orderId)).thenReturn(items);
        when(couponService.validateAndCalculateDiscount(any(ValidateDiscountCommand.class)))
                .thenReturn(DiscountCalculationResult.noDiscount());
        when(pointService.deductPoints(userId, totalAmount)).thenReturn(user);

        // confirmOrder 호출 시 실제 Order 객체 변경
        doAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            int finalAmt = invocation.getArgument(1);
            Instant paidAt = invocation.getArgument(2);
            o.confirm(finalAmt, paidAt);
            return null;
        }).when(orderService).confirmOrder(any(Order.class), anyInt(), any(Instant.class));

        // When
        PaymentResult result = processPaymentUseCase.execute(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.finalAmount()).isEqualTo(totalAmount);
        assertThat(result.discountAmount()).isEqualTo(0);

        // Verify service calls
        verify(orderService).validateForPayment(userId, orderId, fixedNow);
        verify(orderService).getOrderItems(orderId);
        verify(couponService).validateAndCalculateDiscount(any(ValidateDiscountCommand.class));
        verify(productService).decreaseStocks(items);
        verify(pointService).deductPoints(userId, totalAmount);
        verify(orderService).confirmOrder(order, totalAmount, fixedNow);
        verify(orderService).saveDiscountInfo(eq(orderId), any(DiscountCalculation.class));
        verify(pointService).recordUseHistory(userId, totalAmount, user.getBalance());
    }

    @Test
    @DisplayName("쿠폰 적용하여 결제 성공 - 할인 금액 차감")
    void processPayment_withCoupon_success() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        String couponCode = "WELCOME10";
        int totalAmount = 50000;
        int discountAmount = 5000;
        int finalAmount = 45000;
        int userBalance = 100000;

        PaymentCommand command = new PaymentCommand(userId, orderId, couponCode);

        Order order = Order.create(userId, totalAmount, fixedNow.plusSeconds(1800));
        order.assignId(orderId);

        List<OrderItem> items = List.of(
                OrderItem.create(orderId, 1L, "상품A", 10000, 2)
        );

        User user = User.create(userId, "홍길동", "hong@test.com", userBalance);
        DiscountCalculationResult discountResult = new DiscountCalculationResult(1L, 10L, 5000, discountAmount);

        when(orderService.validateForPayment(userId, orderId, fixedNow)).thenReturn(order);
        when(orderService.getOrderItems(orderId)).thenReturn(items);
        when(couponService.validateAndCalculateDiscount(any(ValidateDiscountCommand.class)))
                .thenReturn(discountResult);
        when(pointService.deductPoints(userId, finalAmount)).thenReturn(user);

        // confirmOrder 호출 시 실제 Order 객체 변경
        doAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            int finalAmt = invocation.getArgument(1);
            Instant paidAt = invocation.getArgument(2);
            o.confirm(finalAmt, paidAt);
            return null;
        }).when(orderService).confirmOrder(any(Order.class), anyInt(), any(Instant.class));

        // When
        PaymentResult result = processPaymentUseCase.execute(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.finalAmount()).isEqualTo(finalAmount);
        assertThat(result.discountAmount()).isEqualTo(discountAmount);

        // Verify coupon was marked as used
        verify(couponService).markAsUsed(discountResult.userCouponId());
        verify(orderService).saveDiscountInfo(eq(orderId), any(DiscountCalculation.class));
    }

    @Test
    @DisplayName("주문 만료된 경우 결제 실패")
    void processPayment_expiredOrder_throwsException() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        PaymentCommand command = new PaymentCommand(userId, orderId, null);

        when(orderService.validateForPayment(userId, orderId, fixedNow))
                .thenThrow(new BusinessException("주문이 만료되었습니다", "ORDER_EXPIRED"));

        // When & Then
        assertThatThrownBy(() -> processPaymentUseCase.execute(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("만료");

        verify(orderService).validateForPayment(userId, orderId, fixedNow);
        verifyNoInteractions(couponService, productService, pointService);
    }

    @Test
    @DisplayName("쿠폰이 이미 사용된 경우 결제 실패")
    void processPayment_alreadyUsedCoupon_throwsException() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        String couponCode = "USED_COUPON";
        int totalAmount = 50000;

        PaymentCommand command = new PaymentCommand(userId, orderId, couponCode);

        Order order = Order.create(userId, totalAmount, fixedNow.plusSeconds(1800));
        order.assignId(orderId);

        when(orderService.validateForPayment(userId, orderId, fixedNow)).thenReturn(order);
        when(orderService.getOrderItems(orderId)).thenReturn(List.of());
        when(couponService.validateAndCalculateDiscount(any(ValidateDiscountCommand.class)))
                .thenThrow(new BusinessException("이미 사용된 쿠폰입니다", "COUPON_ALREADY_USED"));

        // When & Then
        assertThatThrownBy(() -> processPaymentUseCase.execute(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 사용");

        verify(orderService).validateForPayment(userId, orderId, fixedNow);
        verify(couponService).validateAndCalculateDiscount(any(ValidateDiscountCommand.class));
        verifyNoInteractions(productService, pointService);
    }

    @Test
    @DisplayName("포인트 부족 시 결제 실패")
    void processPayment_insufficientBalance_throwsException() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        int totalAmount = 50000;

        PaymentCommand command = new PaymentCommand(userId, orderId, null);

        Order order = Order.create(userId, totalAmount, fixedNow.plusSeconds(1800));
        order.assignId(orderId);

        List<OrderItem> items = List.of(
                OrderItem.create(orderId, 1L, "상품A", 10000, 2)
        );

        when(orderService.validateForPayment(userId, orderId, fixedNow)).thenReturn(order);
        when(orderService.getOrderItems(orderId)).thenReturn(items);
        when(couponService.validateAndCalculateDiscount(any(ValidateDiscountCommand.class)))
                .thenReturn(DiscountCalculationResult.noDiscount());
        when(pointService.deductPoints(userId, totalAmount))
                .thenThrow(new InsufficientBalanceException("포인트 잔액 부족"));

        // When & Then
        assertThatThrownBy(() -> processPaymentUseCase.execute(command))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("부족");

        verify(productService).decreaseStocks(items);
        verify(pointService).deductPoints(userId, totalAmount);
        verify(orderService, never()).confirmOrder(any(), anyInt(), any());
    }

    @Test
    @DisplayName("재고 부족 시 결제 실패")
    void processPayment_outOfStock_throwsException() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        int totalAmount = 50000;

        PaymentCommand command = new PaymentCommand(userId, orderId, null);

        Order order = Order.create(userId, totalAmount, fixedNow.plusSeconds(1800));
        order.assignId(orderId);

        List<OrderItem> items = List.of(
                OrderItem.create(orderId, 1L, "상품A", 10000, 2)
        );

        when(orderService.validateForPayment(userId, orderId, fixedNow)).thenReturn(order);
        when(orderService.getOrderItems(orderId)).thenReturn(items);
        when(couponService.validateAndCalculateDiscount(any(ValidateDiscountCommand.class)))
                .thenReturn(DiscountCalculationResult.noDiscount());
        doThrow(new BusinessException("재고 부족", "OUT_OF_STOCK"))
                .when(productService).decreaseStocks(items);

        // When & Then
        assertThatThrownBy(() -> processPaymentUseCase.execute(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("재고");

        verify(productService).decreaseStocks(items);
        verifyNoInteractions(pointService);
        verify(orderService, never()).confirmOrder(any(), anyInt(), any());
    }
}
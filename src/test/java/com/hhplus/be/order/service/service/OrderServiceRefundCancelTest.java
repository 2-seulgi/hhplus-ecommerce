package com.hhplus.be.order.service.service;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.order.domain.Order;
import com.hhplus.be.order.domain.OrderStatus;
import com.hhplus.be.order.infrastructure.OrderRepository;
import com.hhplus.be.order.service.OrderService;
import com.hhplus.be.order.service.dto.RefundCommand;
import com.hhplus.be.order.service.dto.RefundResult;
import com.hhplus.be.coupon.infrastructure.CouponRepository;
import com.hhplus.be.cart.infrastructure.CartRepository;
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
class OrderServiceRefundCancelTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PointRepository pointRepository;
    @Mock private Clock clock;

    @InjectMocks
    private OrderService orderService;

    private Instant fixedNow;

    @BeforeEach
    void setUp() {
        fixedNow = Instant.parse("2025-11-06T10:00:00Z");
        when(clock.instant()).thenReturn(fixedNow);
    }

    @Test
    @DisplayName("환불 성공 - 포인트 환불, 재고 복구, 상태 변경")
    void processRefund_success() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        int finalAmount = 30000;

        // CONFIRMED 상태 주문
        Order confirmedOrder = Order.create(userId, finalAmount, fixedNow.plusSeconds(1800));
        confirmedOrder.assignId(orderId);
        confirmedOrder.confirm(finalAmount, fixedNow.minusSeconds(600)); // 10분 전 결제됨
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(confirmedOrder));

        // 주문 항목들
        List<OrderItem> orderItems = List.of(
                createOrderItem(1L, orderId, 1L, "상품A", 10000, 2),
                createOrderItem(2L, orderId, 2L, "상품B", 10000, 1)
        );
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems);

        // 사용자 (현재 잔액 50000원)
        User user = User.createWithId(userId, "홍길동", "hong@test.com", 50000);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // 상품들 (재고 복구용)
        Product product1 = Product.create("상품A", "설명A", 10000, 10);
        product1.assignId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product1));

        Product product2 = Product.create("상품B", "설명B", 10000, 5);
        product2.assignId(2L);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product2));

        // 포인트 히스토리 저장
        when(pointRepository.save(any(Point.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        RefundCommand command = new RefundCommand(userId, orderId);
        RefundResult result = orderService.processRefund(command);

        // Then
        // 1. 주문 상태가 REFUNDED로 변경
        assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(confirmedOrder.getRefundedAt()).isEqualTo(fixedNow);

        // 2. 포인트 환불 (50000 + 30000 = 80000)
        assertThat(user.getBalance()).isEqualTo(80000);

        // 3. 재고 복구 (10 + 2 = 12, 5 + 1 = 6)
        assertThat(product1.getStock()).isEqualTo(12);
        assertThat(product2.getStock()).isEqualTo(6);

        // 4. 포인트 히스토리 기록
        verify(pointRepository).save(argThat(point ->
                point.getPointType() == PointType.REFUND &&
                point.getAmount() == 30000 &&
                point.getBalanceAfter() == 80000
        ));

        // 5. 결과 검증
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.status()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(result.refundedAmount()).isEqualTo(30000);
        assertThat(result.currentBalance()).isEqualTo(80000);
        assertThat(result.refundedAt()).isEqualTo(fixedNow);
    }

    @Test
    @DisplayName("환불 실패 - 주문을 찾을 수 없음")
    void processRefund_orderNotFound() {
        // Given
        Long userId = 1L;
        Long orderId = 999L;
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // When & Then
        RefundCommand command = new RefundCommand(userId, orderId);
        assertThatThrownBy(() -> orderService.processRefund(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("주문을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("환불 실패 - 다른 사용자의 주문")
    void processRefund_wrongUser() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        Long otherUserId = 2L;

        Order order = Order.create(otherUserId, 30000, fixedNow.plusSeconds(1800));
        order.assignId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // When & Then
        RefundCommand command = new RefundCommand(userId, orderId);
        assertThatThrownBy(() -> orderService.processRefund(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("주문을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("환불 실패 - 이미 환불된 주문은 재환불 불가")
    void processRefund_alreadyRefunded_fail() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;

        // REFUNDED 상태 주문 (이미 환불됨)
        Order refundedOrder = Order.create(userId, 30000, fixedNow.plusSeconds(1800));
        refundedOrder.assignId(orderId);
        refundedOrder.confirm(30000, fixedNow.minusSeconds(1200)); // 결제 완료
        refundedOrder.refund(fixedNow.minusSeconds(600)); // 환불 완료
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(refundedOrder));

        List<OrderItem> orderItems = List.of(
                createOrderItem(1L, orderId, 1L, "상품", 30000, 1)
        );
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems);

        User user = User.createWithId(userId, "홍길동", "hong@test.com", 50000);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Product product = Product.create("상품", "설명", 30000, 10);
        product.assignId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // When & Then
        RefundCommand command = new RefundCommand(userId, orderId);
        assertThatThrownBy(() -> orderService.processRefund(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("환불할 수 없는 주문 상태");
    }

    @Test
    @DisplayName("주문 취소 성공 - PENDING 상태의 주문을 CANCELLED로 변경")
    void cancelOrder_success() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;

        // PENDING 상태 주문
        Order pendingOrder = Order.create(userId, 30000, fixedNow.plusSeconds(1800));
        pendingOrder.assignId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));

        // When
        orderService.cancelOrder(userId, orderId);

        // Then
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("주문 취소 실패 - 주문을 찾을 수 없음")
    void cancelOrder_orderNotFound() {
        // Given
        Long userId = 1L;
        Long orderId = 999L;
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("주문을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("주문 취소 실패 - 다른 사용자의 주문")
    void cancelOrder_wrongUser() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        Long otherUserId = 2L;

        Order order = Order.create(otherUserId, 30000, fixedNow.plusSeconds(1800));
        order.assignId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // When & Then
        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("주문을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("주문 취소 실패 - CONFIRMED 상태의 주문은 취소 불가")
    void cancelOrder_confirmedOrder_fail() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;

        // CONFIRMED 상태 주문 (이미 결제됨)
        Order confirmedOrder = Order.create(userId, 30000, fixedNow.plusSeconds(1800));
        confirmedOrder.assignId(orderId);
        confirmedOrder.confirm(30000, fixedNow.minusSeconds(600));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(confirmedOrder));

        // When & Then
        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("취소할 수 없는 주문 상태");
    }

    @Test
    @DisplayName("주문 취소 실패 - REFUNDED 상태의 주문은 취소 불가")
    void cancelOrder_refundedOrder_fail() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;

        // REFUNDED 상태 주문
        Order refundedOrder = Order.create(userId, 30000, fixedNow.plusSeconds(1800));
        refundedOrder.assignId(orderId);
        refundedOrder.confirm(30000, fixedNow.minusSeconds(600));
        refundedOrder.refund(fixedNow.minusSeconds(300));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(refundedOrder));

        // When & Then
        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("취소할 수 없는 주문 상태");
    }

    // 헬퍼 메서드
    private OrderItem createOrderItem(Long id, Long orderId, Long productId,
                                      String productName, int unitPrice, int quantity) {
        OrderItem item = OrderItem.create(orderId, productId, productName, unitPrice, quantity);
        item.assignId(id);
        return item;
    }
}
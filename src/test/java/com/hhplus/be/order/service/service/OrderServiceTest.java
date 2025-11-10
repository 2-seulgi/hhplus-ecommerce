package com.hhplus.be.order.service.service;

import com.hhplus.be.cart.domain.CartItem;
import com.hhplus.be.cart.infrastructure.CartRepository;
import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.InvalidInputException;
import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.order.domain.Order;
import com.hhplus.be.order.domain.OrderStatus;
import com.hhplus.be.order.infrastructure.OrderRepository;
import com.hhplus.be.orderitem.domain.OrderItem;
import com.hhplus.be.orderitem.infrastructure.OrderItemRepository;
import com.hhplus.be.point.domain.Point;
import com.hhplus.be.point.domain.PointType;
import com.hhplus.be.point.infrastructure.PointRepository;
import com.hhplus.be.point.service.PointService;
import com.hhplus.be.product.domain.Product;
import com.hhplus.be.product.infrastructure.ProductRepository;
import com.hhplus.be.user.domain.User;
import com.hhplus.be.user.infrastructure.UserRepository;
import com.hhplus.be.order.service.OrderService;
import com.hhplus.be.coupon.infrastructure.CouponRepository;
import com.hhplus.be.usercoupon.infrastructure.UserCouponRepository;
import com.hhplus.be.orderdiscount.infrastructure.OrderDiscountRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CartRepository cartRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PointRepository pointRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private UserCouponRepository userCouponRepository;
    @Mock private OrderDiscountRepository orderDiscountRepository;
    @Mock private Clock clock;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("장바구니로 주문 생성 성공 - PENDING/총액/만료시간 설정")
    void createFromCart_success() {
        // given
        Long userId = 1L;
        Instant fixedNow = Instant.parse("2025-01-01T10:00:00Z");
        when(clock.instant()).thenReturn(fixedNow);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.createWithId(userId, "홍길동", "hong@example.com", 10_000)));

        var cartItems = List.of(
                CartItem.create(userId, 1L, 2) // productId=1, qty=2
        );
        when(cartRepository.findByUserId(userId)).thenReturn(cartItems);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(Product.create("맥북 PRO", "M3", 3_000_000, 10)));

        // save 시 ID 부여
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.assignId(12345L);
            return o;
        });

        // when
        var result = orderService.createFromCart(userId);

        // then
        assertThat(result.orderId()).isEqualTo(12345L);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.totalAmount()).isEqualTo(6_000_000);

        // expiresAt = fixedNow + 30분
        Instant expectedExpiry = fixedNow.plus(Duration.ofMinutes(30));
        assertThat(result.expiresAt()).isEqualTo(expectedExpiry);

        verify(orderItemRepository).saveAll(anyList());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("장바구니가 비어있으면 400(InvalidInputException)")
    void createFromCart_emptyCart_400() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.createWithId(userId, "홍길동", "hong@example.com", 10_000)));
        when(cartRepository.findByUserId(userId)).thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> orderService.createFromCart(userId))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("장바구니");
        verify(orderRepository, never()).save(any());
        verify(orderItemRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("회원 없음이면 404(ResourceNotFoundException)")
    void createFromCart_userNotFound_404() {
        // given
        Long userId = 99L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.createFromCart(userId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(cartRepository, productRepository, orderRepository, orderItemRepository);
    }

    @Test
    @DisplayName("재고 0 상품 포함 시 409(BusinessException/OUT_OF_STOCK)")
    void createFromCart_stockZero_409() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.createWithId(userId, "홍길동", "hong@example.com", 0)));

        when(cartRepository.findByUserId(userId))
                .thenReturn(List.of(CartItem.create(userId, 1L, 1)));

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(Product.create("품절상품", "N/A", 5_000, 0)));

        // when & then
        assertThatThrownBy(() -> orderService.createFromCart(userId))
                .isInstanceOf(BusinessException.class);
        verify(orderRepository, never()).save(any());
        verify(orderItemRepository, never()).saveAll(anyList());
    }

}

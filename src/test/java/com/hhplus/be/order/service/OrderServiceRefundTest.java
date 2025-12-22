package com.hhplus.be.order.service;

import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.domain.model.OrderStatus;
import com.hhplus.be.order.domain.repository.OrderRepository;
import com.hhplus.be.order.service.dto.RefundCommand;
import com.hhplus.be.order.service.dto.RefundResult;
import com.hhplus.be.orderitem.domain.model.OrderItem;
import com.hhplus.be.orderitem.domain.repository.OrderItemRepository;
import com.hhplus.be.point.domain.repository.PointRepository;
import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.domain.repository.ProductRepository;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.user.domain.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OrderService 환불/취소 테스트
 * <p>
 * 도메인 객체 변경 후 save() 호출 검증
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceRefundTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PointRepository pointRepository;

    @InjectMocks private OrderService orderService;

    private Instant fixedNow;
    private Clock clock;

    @BeforeEach
    void setUp() {
        fixedNow = Instant.parse("2025-01-01T10:00:00Z");
        clock = Clock.fixed(fixedNow, ZoneId.systemDefault());

        // Clock을 수동으로 주입
        orderService = new OrderService(
                orderRepository,
                userRepository,
                null, // carts
                productRepository,
                orderRepository, // orders
                orderItemRepository,
                pointRepository,
                null, // orderDiscountRepository
                clock
        );
    }

    @Test
    @DisplayName("환불 처리 시 User, Product, Order 모두 save() 호출됨")
    void processRefund_savesAllDomainObjects() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        Long productId = 10L;
        int refundAmount = 50000;
        int userBalance = 100000;
        int productStock = 5;

        RefundCommand command = new RefundCommand(userId, orderId);

        // Order 생성 및 확정
        Order order = Order.create(userId, refundAmount, fixedNow.minusSeconds(3600));
        assignOrderId(order, orderId);
        order.confirm(refundAmount, fixedNow.minusSeconds(1800));

        // OrderItem 생성
        OrderItem orderItem = OrderItem.create(orderId, productId, "테스트 상품", 10000, 2);

        // User 생성
        User user = User.create(userId, "테스트유저", "test@test.com", userBalance);

        // Product 생성
        Product product = Product.create("테스트 상품", "설명", 10000, productStock);
        assignProductId(product, productId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(pointRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefundResult result = orderService.processRefund(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(OrderStatus.REFUNDED);

        // Verify save() 호출 확인
        verify(userRepository).save(user); // User 저장 확인
        verify(productRepository).save(product); // Product 저장 확인
        verify(orderRepository).save(order); // Order 저장 확인
        verify(pointRepository).save(any()); // Point 히스토리 저장 확인
    }

    @Test
    @DisplayName("주문 취소 시 Order save() 호출됨")
    void cancelOrder_savesOrder() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;

        Order order = Order.create(userId, 50000, fixedNow.plusSeconds(1800));
        assignOrderId(order, orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // When
        orderService.cancelOrder(userId, orderId);

        // Then
        verify(orderRepository).save(order); // Order 저장 확인
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // Helper method for ID assignment
    private void assignOrderId(Order order, Long id) {
        try {
            var field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, id);
        } catch (Exception e) {
            throw new RuntimeException("ID 할당 실패", e);
        }
    }

    private void assignProductId(Product product, Long id) {
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (Exception e) {
            throw new RuntimeException("ID 할당 실패", e);
        }
    }
}
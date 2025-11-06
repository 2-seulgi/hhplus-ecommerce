package com.hhplus.be.order.controller;

import com.hhplus.be.order.controller.dto.*;
import com.hhplus.be.order.service.OrderService;
import com.hhplus.be.order.service.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 주문 Controller
 * API 명세 기반 구현
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    /**
     * 주문 생성
     * POST /users/{userId}/orders
     * 장바구니의 상품으로 주문을 생성합니다.
     */
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@PathVariable Long userId) {
        CreateOrderResult result = orderService.createFromCart(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateOrderResponse.from(result));
    }

    /**
     * 주문 내역 조회
     * GET /users/{userId}/orders
     * 사용자의 주문 내역을 조회합니다.
     */
    @GetMapping
    public ResponseEntity<OrderListResponse> getOrderList(@PathVariable Long userId) {
        OrderListQuery query = new OrderListQuery(userId);
        OrderListResult result = orderService.getOrderList(query);
        return ResponseEntity.ok(OrderListResponse.from(result));
    }

    /**
     * 주문 상세 조회
     * GET /users/{userId}/orders/{orderId}
     * 특정 주문의 상세 정보를 조회합니다.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            @PathVariable Long userId,
            @PathVariable Long orderId
    ) {
        OrderDetailQuery query = new OrderDetailQuery(userId, orderId);
        OrderDetailResult result = orderService.getOrderDetail(query);
        return ResponseEntity.ok(OrderDetailResponse.from(result));
    }

    /**
     * 주문 결제
     * POST /users/{userId}/orders/{orderId}/payment
     * 주문을 결제합니다. 쿠폰을 적용할 수 있습니다.
     */
    @PostMapping("/{orderId}/payment")
    public ResponseEntity<PaymentResponse> processPayment(
            @PathVariable Long userId,
            @PathVariable Long orderId,
            @RequestBody(required = false) PaymentRequest request
    ) {
        String couponCode = (request != null) ? request.couponCode() : null;
        PaymentCommand command = new PaymentCommand(userId, orderId, couponCode);
        PaymentResult result = orderService.processPayment(command);
        return ResponseEntity.ok(PaymentResponse.from(result));
    }

    /**
     * 주문 환불
     * POST /users/{userId}/orders/{orderId}/refund
     * 결제 완료된 주문을 환불합니다.
     */
    @PostMapping("/{orderId}/refund")
    public ResponseEntity<RefundResponse> processRefund(
            @PathVariable Long userId,
            @PathVariable Long orderId
    ) {
        RefundCommand command = new RefundCommand(userId, orderId);
        RefundResult result = orderService.processRefund(command);
        return ResponseEntity.ok(RefundResponse.from(result));
    }

    /**
     * 주문 취소
     * POST /users/{userId}/orders/{orderId}/cancel
     * PENDING 상태의 주문을 취소합니다.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable Long userId,
            @PathVariable Long orderId
    ) {
        orderService.cancelOrder(userId, orderId);
        return ResponseEntity.noContent().build();
    }
}
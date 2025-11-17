package com.hhplus.be.order.service;

import com.hhplus.be.cart.domain.model.CartItem;
import com.hhplus.be.cart.domain.repository.CartRepository;
import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.InvalidInputException;
import com.hhplus.be.common.exception.ResourceNotFoundException;

import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.domain.model.OrderStatus;
import com.hhplus.be.order.domain.repository.OrderRepository;
import com.hhplus.be.order.service.dto.CreateOrderResult;
import com.hhplus.be.order.service.dto.OrderDetailQuery;
import com.hhplus.be.order.service.dto.OrderDetailResult;
import com.hhplus.be.order.service.dto.OrderListQuery;
import com.hhplus.be.order.service.dto.OrderListResult;
import com.hhplus.be.order.service.dto.RefundCommand;
import com.hhplus.be.order.service.dto.RefundResult;
import com.hhplus.be.usercoupon.service.dto.DiscountCalculation;
import com.hhplus.be.orderitem.domain.model.OrderItem;
import com.hhplus.be.orderitem.domain.repository.OrderItemRepository;
import com.hhplus.be.point.domain.model.Point;
import com.hhplus.be.point.domain.repository.PointRepository;
import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.domain.repository.ProductRepository;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.user.domain.repository.UserRepository;
import com.hhplus.be.orderdiscount.domain.OrderDiscount;
import com.hhplus.be.orderdiscount.domain.repository.OrderDiscountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private static final long EXPIRE_MINUTES = 30L;

    private final OrderRepository orderRepository;
    private final UserRepository users;
    private final CartRepository carts;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final PointRepository pointRepository;
    private final OrderDiscountRepository orderDiscountRepository;
    private final Clock clock;

    /**
     * 장바구니 기반 주문 생성
     * 규칙:
     *  - 회원 존재 필수 (404)
     *  - 장바구니 비어있으면 (400)
     *  - 재고 0 상품 포함 시 거부 (409: BusinessException("...","OUT_OF_STOCK"))
     *  - 총액 = 주문 시점 단가 스냅샷 × 수량
     *  - 만료시간 = now + 30분
     */
    @Transactional
    public CreateOrderResult createFromCart(Long userId) {
        // 1) 회원 검증
        users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 회원"));

        // 2) 장바구니 조회/검증
        List<CartItem> cart = carts.findByUserId(userId);
        if (cart.isEmpty()) {
            throw new InvalidInputException("장바구니가 비어있음");
        }

        // 3) 제품 조회/재고 검증 + 총액 계산을 먼저 수행(스냅샷 준비)
        //    Product.price(현재가)를 OrderItem.unitPrice(스냅샷)에 복사할 예정
        record Line(Product p, int qty) {}
        List<Line> lines = new ArrayList<>();
        int totalAmount = 0;

        for (CartItem ci : cart) {
            Product p = products.findById(ci.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("상품 없음: " + ci.getProductId()));
            // 재고 0이면 주문 생성 거부 (결제 시점에 실제 차감)
            if (p.getStock() <= 0) {
                throw new BusinessException("품절 상품이 포함되어 주문을 생성할 수 없습니다: " + p.getName(), "OUT_OF_STOCK");
            }
            int unitPriceSnapshot = p.getPrice(); // 스냅샷
            totalAmount += unitPriceSnapshot * ci.getQuantity();
            lines.add(new Line(p, ci.getQuantity()));
        }

        // 4) 주문 저장( orderId 확보 )
        Instant now = Instant.now(clock);
        Order pending = Order.create(userId, totalAmount, now.plus(EXPIRE_MINUTES, ChronoUnit.MINUTES));
        Order saved = orders.save(pending); // 인메모리면 여기서 assignId 수행

        // 5) 주문항목 생성(스냅샷) 후 일괄 저장
        List<OrderItem> items = lines.stream()
                .map(l -> OrderItem.create(
                        saved.getId(),
                        l.p().getId(),
                        l.p().getName(),
                        l.p().getPrice(), // 스냅샷 단가
                        l.qty()
                ))
                .toList();

        orderItems.saveAll(items);

        // 6) 결과 DTO
        return CreateOrderResult.from(saved, items);
    }

    /**
     * 주문 내역 조회
     * 규칙:
     *  - 회원 존재 필수 (404)
     *  - 해당 회원의 모든 주문을 최신순으로 조회
     */
    @Transactional(readOnly = true)
    public OrderListResult getOrderList(OrderListQuery query) {
        // 1) 회원 검증
        users.findById(query.userId())
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 회원"));

        // 2) 주문 목록 조회
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(query.userId());

        if (orders.isEmpty()) {
            return new OrderListResult(List.of());
        }

        // 3) 주문 항목 일괄 조회 (N+1 방지)
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        List<OrderItem> allItems = orderItems.findByOrderIdIn(orderIds);

        // 4) 주문별로 항목 그룹화
        Map<Long, List<OrderItem>> itemsByOrderId = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        // 5) 결과 DTO
        return OrderListResult.from(orders, itemsByOrderId);
    }

    /**
     * 주문 상세 조회
     * 규칙:
     *  - 회원 존재 필수 (404)
     *  - 주문 존재 필수 (404)
     *  - 주문이 해당 회원의 것이 아니면 (404)
     */
    @Transactional(readOnly = true)
    public OrderDetailResult getOrderDetail(OrderDetailQuery query) {
        // 1) 회원 검증
        users.findById(query.userId())
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 회원"));

        // 2) 주문 조회 및 소유권 검증
        Order order = orderRepository.findById(query.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다"));

        if (!order.getUserId().equals(query.userId())) {
            throw new ResourceNotFoundException("주문을 찾을 수 없습니다");
        }

        // 3) 주문 항목 조회
        List<OrderItem> items = orderItems.findByOrderId(query.orderId());

        // 4) 결과 DTO
        return OrderDetailResult.from(order, items);
    }
    /**
     * 환불 처리
     *
     * 트랜잭션 처리 순서:
     *  1. 주문 조회 및 검증 (CONFIRMED, 소유자 확인)
     *  2. 포인트 환불
     *  3. 재고 복구
     *  4. 주문 상태 변경 (CONFIRMED → REFUNDED)
     *  5. 포인트 히스토리 기록
     *  6. 결과 반환
     */
    @Transactional
    public RefundResult processRefund(RefundCommand command) {
        Instant now = Instant.now(clock);

        // 1. 주문 조회 및 검증
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다"));

        // 소유권 검증
        if (!order.getUserId().equals(command.userId())) {
            throw new ResourceNotFoundException("주문을 찾을 수 없습니다");
        }

        // 2. 주문 항목 조회
        List<OrderItem> items = orderItems.findByOrderId(command.orderId());

        // 3. 포인트 환불
        User user = users.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다"));

        int refundAmount = order.getFinalAmount();
        user.charge(refundAmount);

        // 4. 재고 복구
        for (OrderItem item : items) {
            Product product = products.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));
            product.increaseStock(item.getQuantity());
        }

        // 5. 주문 상태 변경 (CONFIRMED → REFUNDED)
        order.refund(now);

        // 6. 포인트 히스토리 기록
        Point pointHistory = Point.refund(command.userId(), refundAmount, user.getBalance());
        pointRepository.save(pointHistory);

        // 7. 결과 반환
        return new RefundResult(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                refundAmount,
                user.getBalance(),
                order.getRefundedAt()
        );
    }

    /**
     * 주문 취소
     * PENDING 상태의 주문을 취소합니다.
     */
    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Instant now = Instant.now(clock);

        // 1. 주문 조회 및 검증
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다"));

        // 소유권 검증
        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("주문을 찾을 수 없습니다");
        }

        // 2. 주문 취소 (PENDING → CANCELLED)
        order.cancel(now);
    }

    /**
     * 결제를 위한 주문 검증 (UseCase용)
     */
    public Order validateForPayment(Long userId, Long orderId, Instant now) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다"));

        // 소유권 검증
        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("주문을 찾을 수 없습니다");
        }

        // 상태 검증
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("결제할 수 없는 주문 상태입니다", "INVALID_ORDER_STATUS");
        }

        // 만료 검증
        if (order.isExpired(now)) {
            throw new BusinessException("주문이 만료되었습니다", "ORDER_EXPIRED");
        }

        return order;
    }

    /**
     * 주문 항목 조회 (UseCase용)
     */
    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItems.findByOrderId(orderId);
    }

    /**
     * 주문 확정 (UseCase용)
     */
    public void confirmOrder(Order order, int finalAmount, Instant paidAt) {
        order.confirm(finalAmount, paidAt);
    }

    /**
     * 할인 정보 저장 (UseCase용)
     */
    public void saveDiscountInfo(Long orderId, DiscountCalculation discount) {
        if (!discount.hasDiscount()) {
            return;
        }

        OrderDiscount orderDiscount = OrderDiscount.createCouponDiscount(
                orderId,
                discount.userCouponId(),
                discount.discountValue(),
                discount.discountAmount()
        );
        orderDiscountRepository.save(orderDiscount);
    }
}

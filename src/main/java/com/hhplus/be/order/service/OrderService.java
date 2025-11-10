package com.hhplus.be.order.service;

import com.hhplus.be.cart.domain.CartItem;
import com.hhplus.be.cart.infrastructure.CartRepository;
import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.InvalidInputException;
import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.coupon.domain.Coupon;
import com.hhplus.be.coupon.domain.DiscountType;
import com.hhplus.be.coupon.infrastructure.CouponRepository;
import com.hhplus.be.order.domain.Order;
import com.hhplus.be.order.domain.OrderStatus;
import com.hhplus.be.order.infrastructure.OrderRepository;
import com.hhplus.be.order.service.dto.CreateOrderResult;
import com.hhplus.be.order.service.dto.OrderDetailQuery;
import com.hhplus.be.order.service.dto.OrderDetailResult;
import com.hhplus.be.order.service.dto.OrderListQuery;
import com.hhplus.be.order.service.dto.OrderListResult;
import com.hhplus.be.order.service.dto.PaymentCommand;
import com.hhplus.be.order.service.dto.PaymentResult;
import com.hhplus.be.order.service.dto.RefundCommand;
import com.hhplus.be.order.service.dto.RefundResult;
import com.hhplus.be.orderitem.domain.OrderItem;
import com.hhplus.be.orderitem.infrastructure.OrderItemRepository;
import com.hhplus.be.point.domain.Point;
import com.hhplus.be.point.infrastructure.PointRepository;
import com.hhplus.be.product.domain.Product;
import com.hhplus.be.product.infrastructure.ProductRepository;
import com.hhplus.be.user.domain.User;
import com.hhplus.be.user.infrastructure.UserRepository;
import com.hhplus.be.orderdiscount.domain.OrderDiscount;
import com.hhplus.be.orderdiscount.infrastructure.OrderDiscountRepository;
import com.hhplus.be.usercoupon.UserCoupon;
import com.hhplus.be.usercoupon.infrastructure.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
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
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final OrderDiscountRepository orderDiscountRepository;
    private final Clock clock; // 테스트용 주입

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
                        l.p().getProduct_id(),
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
        List<Order> orders = orderRepository.findByUserId(query.userId());

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
     * 결제 처리 (시퀀스 다이어그램 참조)
     *
     * 트랜잭션 처리 순서:
     *  1. 주문 조회 및 검증 (PENDING, 만료 안됨, 소유자 확인)
     *  2. 주문 항목 조회
     *  3. 쿠폰 검증 (존재, 사용 기간, 보유 여부)
     *  4. 재고 차감 (각 상품별, 낙관적 락)
     *  5. 최종 금액 계산 (totalAmount - discountAmount)
     *  6. 포인트 차감 (낙관적 락)
     *  7. 쿠폰 사용 처리
     *  8. 주문 상태 변경 (PENDING → CONFIRMED)
     *  9. 할인 정보 저장 (OrderDiscount)
     * 10. 포인트 히스토리 기록
     * 11. 결과 반환
     *
     * 실패 시나리오:
     *  - 주문 없음 → ResourceNotFoundException
     *  - 소유자 불일치 → ResourceNotFoundException
     *  - 주문 상태가 PENDING 아님 → BusinessException("INVALID_ORDER_STATUS")
     *  - 주문 만료됨 → BusinessException("ORDER_EXPIRED")
     *  - 쿠폰 없음/보유X → BusinessException("COUPON_INVALID")
     *  - 쿠폰 사용 기간 아님 → BusinessException("COUPON_EXPIRED")
     *  - 쿠폰 이미 사용 → BusinessException("ALREADY_USED")
     *  - 재고 부족 → BusinessException("OUT_OF_STOCK") by Product.decreaseStock()
     *  - 포인트 부족 → InsufficientBalanceException by User.use()
     */
    @Transactional
    public PaymentResult processPayment(PaymentCommand command) {
        // 1. 주문 조회 및 검증
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다"));

        // 소유권 검증 (다른 사용자의 주문은 결제 불가)
        if (!order.getUserId().equals(command.userId())) {
            throw new ResourceNotFoundException("주문을 찾을 수 없습니다");
        }

        // 상태 검증 (PENDING만 결제 가능)
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("결제할 수 없는 주문 상태입니다", "INVALID_ORDER_STATUS");
        }

        // 만료 검증 (만료된 주문은 결제 불가)
        Instant now = Instant.now(clock);
        if (order.isExpired(now)) {
            throw new BusinessException("주문이 만료되었습니다", "ORDER_EXPIRED");
        }

        // 2. 주문 항목 조회
        List<OrderItem> items = orderItems.findByOrderId(command.orderId());

        // 3. 쿠폰 검증 및 할인 금액 계산
        int discountAmount = 0;
        UserCoupon userCoupon = null;
        Coupon coupon = null;

        if (command.couponCode() != null && !command.couponCode().isBlank()) {
            // 쿠폰 조회
            coupon = couponRepository.findByCode(command.couponCode())
                    .orElseThrow(() -> new BusinessException("유효하지 않은 쿠폰입니다", "COUPON_INVALID"));

            // 사용 기간 검증
            if (now.isBefore(coupon.getUseStartAt()) || now.isAfter(coupon.getUseEndAt())) {
                throw new BusinessException("쿠폰 사용 기간이 아닙니다", "COUPON_EXPIRED");
            }

            // 사용자 쿠폰 조회
            userCoupon = userCouponRepository.findByUserIdAndCouponId(command.userId(), coupon.getId())
                    .orElseThrow(() -> new BusinessException("보유하지 않은 쿠폰입니다", "COUPON_INVALID"));

            // 사용한 쿠폰
            if (userCoupon.isUsed()) {
                throw new BusinessException("이미 사용된 쿠폰입니다", "COUPON_ALREADY_USED");
            }

            // 할인 금액 계산
            if (coupon.getDiscountType() == DiscountType.FIXED) {
                discountAmount = coupon.getDiscountValue();
            } else if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
                discountAmount = order.getTotalAmount() * coupon.getDiscountValue() / 100;
            }
        }

        // 4. 재고 차감 (각 상품별로, 낙관적 락)
        for (OrderItem item : items) {
            Product product = products.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));

            // @Version 필드로 낙관적 락 적용 (동시성 제어)
            product.decreaseStock(item.getQuantity());
        }

        // 5. 최종 금액 계산 및 포인트 차감
        int finalAmount = Math.max(0, order.getTotalAmount() - discountAmount);

        User user = users.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다"));

        // @Version 필드로 낙관적 락 적용 (동시성 제어)
        user.use(finalAmount);

        // 6. 쿠폰 사용 처리
        if (userCoupon != null) {
            userCoupon.use(); // 이미 사용된 경우 BusinessException("ALREADY_USED") 발생
        }

        // 7. 주문 상태 변경 (PENDING → CONFIRMED)
        order.confirm(finalAmount, now);

        // 8. 할인 정보 저장
        if (userCoupon != null) {
            OrderDiscount orderDiscount = OrderDiscount.createCouponDiscount(
                    order.getId(),
                    userCoupon.getId(),
                    coupon.getDiscountValue(),
                    discountAmount
            );
            orderDiscountRepository.save(orderDiscount);
        }

        // 9. 포인트 히스토리 기록
        Point pointHistory = Point.use(command.userId(), finalAmount, user.getBalance());
        pointRepository.save(pointHistory);

        // 10. 결과 반환
        return PaymentResult.from(order, user, discountAmount);
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
}

package com.hhplus.be.order.usecase;

import com.hhplus.be.coupon.service.dto.ValidateDiscountCommand;
import com.hhplus.be.order.domain.Order;
import com.hhplus.be.order.service.OrderService;
import com.hhplus.be.order.service.dto.PaymentCommand;
import com.hhplus.be.order.service.dto.PaymentResult;
import com.hhplus.be.orderitem.domain.OrderItem;
import com.hhplus.be.point.service.PointService;
import com.hhplus.be.product.service.ProductService;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.coupon.service.CouponService;
import com.hhplus.be.usercoupon.service.dto.DiscountCalculation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessPaymentUseCase {
    // Domain Service들만 의존
    private final OrderService orderService;
    private final CouponService couponService;
    private final ProductService productService;
    private final PointService pointService;
    private final Clock clock;

    /**
     * 결제 처리 - 여러 도메인 서비스를 조율
     */
    @Transactional
    public PaymentResult execute(PaymentCommand command) {
        Instant now = Instant.now(clock);

        // 1. 주문 검증 (Order 도메인)
        Order order = orderService.validateForPayment(
                command.userId(),
                command.orderId(),
                now
        );

        // 2. 주문 항목 조회 (Order 도메인)
        List<OrderItem> items = orderService.getOrderItems(command.orderId());

        // 3. 쿠폰 할인 계산 (Coupon 도메인)
        var couponResult = couponService.validateAndCalculateDiscount(
                new ValidateDiscountCommand(command.userId(), command.couponCode() , order.getTotalAmount())
        );

        // 매핑 (쿠폰 도메인 -> 주문 도메인)
        var discount = new DiscountCalculation(
                couponResult.userCouponId(),
                couponResult.couponId(),
                couponResult.discountValue(),
                couponResult.discountAmount()
        );

        // 4. 재고 차감 (Product 도메인)
        productService.decreaseStocks(items);

        // 5. 포인트 차감 (Point 도메인)
        int finalAmount = Math.max(0, order.getTotalAmount() - discount.discountAmount());
        User user = pointService.deductPoints(command.userId(), finalAmount);

        // 6. 쿠폰 사용 처리 (Coupon 도메인)
        if (discount.hasDiscount()) {
            couponService.markAsUsed(discount.userCouponId());
        }

        // 7. 주문 확정 (Order 도메인)
        orderService.confirmOrder(order, finalAmount, now);

        // 8. 할인 정보 저장 (Order 도메인)
        orderService.saveDiscountInfo(order.getId(), discount);

        // 9. 포인트 히스토리 기록 (Point 도메인)
        pointService.recordUseHistory(command.userId(), finalAmount, user.getBalance());

        return PaymentResult.from(order, user, discount.discountAmount());
    }
}
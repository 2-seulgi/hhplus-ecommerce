package com.hhplus.be.order.usecase;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.LockAcquisitionException;
import com.hhplus.be.coupon.service.dto.ValidateDiscountCommand;
import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.service.OrderService;
import com.hhplus.be.order.service.dto.PaymentCommand;
import com.hhplus.be.order.service.dto.PaymentResult;
import com.hhplus.be.orderitem.domain.model.OrderItem;
import com.hhplus.be.point.service.PointService;
import com.hhplus.be.product.service.ProductStockService;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.coupon.service.CouponService;
import com.hhplus.be.usercoupon.service.dto.DiscountCalculation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessPaymentUseCase {
    // Domain Service들만 의존
    private final OrderService orderService;
    private final CouponService couponService;
    private final ProductStockService productStockService;
    private final PointService pointService;
    private final Clock clock;

    /**
     * 결제 처리 - 여러 도메인 서비스를 조율
     *
     * 보상 트랜잭션 전략:
     * - 재고 차감은 REQUIRES_NEW로 즉시 커밋 (성능 최적화)
     * - 이후 단계 실패 시 수동으로 재고 복원
     * - 쿠폰 사용 실패 시 전체 롤백
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

        // 보상 트랜잭션 추적 변수
        boolean stockDecreased = false;
        boolean pointsDeducted = false;
        Long usedCouponId = null;

        try {
            // 4. 재고 차감 (분산락 + REQUIRES_NEW)
            try {
                productStockService.decreaseStocksWithLock(items);
                stockDecreased = true;
            } catch (LockAcquisitionException e) {
                throw new BusinessException("재고 처리 중 오류가 발생했습니다", "STOCK_LOCK_FAILED");
            }

            // 5. 포인트 차감 (비관적 락)
            int finalAmount = Math.max(0, order.getTotalAmount() - discount.discountAmount());
            User user = pointService.deductPoints(command.userId(), finalAmount);
            pointsDeducted = true;

            // 6. 쿠폰 사용 처리 (분산락)
            if (discount.hasDiscount()) {
                couponService.markAsUsed(discount.userCouponId());
                usedCouponId = discount.userCouponId();
            }

            // 7. 주문 확정 (Order 도메인)
            orderService.confirmOrder(order, finalAmount, now);

            // 8. 할인 정보 저장 (Order 도메인)
            orderService.saveDiscountInfo(order.getId(), discount);

            // 9. 포인트 히스토리 기록 (Point 도메인)
            pointService.recordUseHistory(command.userId(), finalAmount, user.getBalance());

            return PaymentResult.from(order, user, discount.discountAmount());

        } catch (Exception e) {
            // ========== 보상 트랜잭션 시작 ==========
            handlePaymentFailure(items, command.userId(),
                Math.max(0, order.getTotalAmount() - discount.discountAmount()),
                stockDecreased, pointsDeducted, usedCouponId, e);
            throw e;  // 원본 예외 재전파
        }
    }

    /**
     * 결제 실패 시 보상 트랜잭션 처리
     *
     * 복원 순서 (역순):
     * 1. 쿠폰 복원
     * 2. 포인트 환불
     * 3. 재고 복원
     *
     * 각 복원 작업은 독립적으로 시도되며, 일부 실패해도 다른 복원은 계속 진행됨
     */
    private void handlePaymentFailure(
            List<OrderItem> items,
            Long userId,
            int finalAmount,
            boolean stockDecreased,
            boolean pointsDeducted,
            Long usedCouponId,
            Exception originalException) {

        log.error("결제 실패, 보상 트랜잭션 시작 - userId: {}, orderId: {}, 원인: {}",
            userId, items.isEmpty() ? "N/A" : items.get(0).getOrderId(),
            originalException.getMessage(), originalException);

        try {
            // 1. 쿠폰 복원 (사용 처리된 경우)
            if (usedCouponId != null) {
                try {
                    log.info("쿠폰 복원 시작 - userCouponId: {}", usedCouponId);
                    couponService.restoreCoupon(usedCouponId);
                    log.info("쿠폰 복원 성공 - userCouponId: {}", usedCouponId);
                } catch (Exception e) {
                    // 쿠폰 복원 실패는 로그만 남김 (결제는 이미 실패했으므로 사용자에게 영향 적음)
                    log.error("쿠폰 복원 실패 - userCouponId: {}", usedCouponId, e);
                }
            }

            // 2. 포인트 환불 (차감된 경우)
            if (pointsDeducted) {
                try {
                    log.info("포인트 환불 시작 - userId: {}, amount: {}", userId, finalAmount);
                    pointService.refundPoints(userId, finalAmount);
                    pointService.recordRefundHistory(userId, finalAmount,
                        pointService.getBalance(new com.hhplus.be.point.service.dto.PointBalanceQuery(userId)).balance());
                    log.info("포인트 환불 성공 - userId: {}, amount: {}", userId, finalAmount);
                } catch (Exception e) {
                    // 포인트 환불 실패는 심각 (사용자 금전 피해)
                    log.error("⚠️ [CRITICAL] 포인트 환불 실패 - userId: {}, amount: {} - 수동 처리 필요!",
                        userId, finalAmount, e);
                    // TODO: 실무에서는 알람 발송, 수동 처리 큐에 추가, Slack 알림 등
                }
            }

            // 3. 재고 복원 (차감된 경우)
            if (stockDecreased) {
                try {
                    log.info("재고 복원 시작 - items: {}", items.size());
                    productStockService.increaseStocksWithLock(items);
                    log.info("재고 복원 성공 - items: {}", items.size());
                } catch (Exception e) {
                    // 재고 복원 실패는 심각 (다른 사용자가 구매 못함)
                    log.error("⚠️ [CRITICAL] 재고 복원 실패 - items: {} - 수동 처리 필요!",
                        items, e);
                    // TODO: 실무에서는 알람 발송, 수동 처리 큐에 추가, Slack 알림 등
                }
            }

        } catch (Exception e) {
            // 보상 트랜잭션 자체가 실패 (최악의 경우)
            log.error("⚠️⚠️ [CRITICAL] 보상 트랜잭션 전체 실패 - userId: {} - 긴급 대응 필요!",
                userId, e);
            // TODO: 실무에서는 즉시 알람, 별도 복구 프로세스 실행
        }
    }
}
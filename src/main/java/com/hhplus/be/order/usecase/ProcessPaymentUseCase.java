package com.hhplus.be.order.usecase;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.LockAcquisitionException;
import com.hhplus.be.coupon.service.dto.ValidateDiscountCommand;
import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.service.OrderService;
import com.hhplus.be.order.service.PaymentTransactionService;
import com.hhplus.be.order.service.dto.PaymentCommand;
import com.hhplus.be.order.service.dto.PaymentResult;
import com.hhplus.be.orderitem.domain.model.OrderItem;
import com.hhplus.be.product.service.ProductStockService;
import com.hhplus.be.coupon.service.CouponService;
import com.hhplus.be.usercoupon.service.dto.DiscountCalculation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final PaymentTransactionService paymentTransactionService;
    private final Clock clock;

    /**
     * 결제 처리 - 여러 도메인 서비스를 조율
     * <p>
     * 보상 트랜잭션 전략:
     * - 재고 차감은 REQUIRES_NEW로 즉시 커밋 (성능 최적화)
     * - 이후 단계 실패 시 수동으로 재고 복원
     * - 쿠폰 사용 실패 시 전체 롤백
     */
    public PaymentResult execute(PaymentCommand command) {
        Instant now = Instant.now(clock);
        // 1. 결제 검증 및 사전 계산 (트랜잭션 불필요)
        PaymentValidationResult validated = validatePayment(command, now);

        // 2. 재고 차감 (트랜잭션 외부, 분산락 + REQUIRES_NEW)
        boolean stockDecreased = false;
        try {
            productStockService.decreaseStocksWithLock(validated.items());
            stockDecreased = true;
        } catch (LockAcquisitionException e) {
            throw new BusinessException("재고 처리 중 오류가 발생했습니다", "STOCK_LOCK_FAILED");
        }

        // 3. 트랜잭션 처리 (포인트, 쿠폰, 주문 확정)
        // PaymentTransactionService를 통해 프록시 경로로 호출하여 @Transactional 보장
        try {
            return paymentTransactionService.executeInTransaction(
                    command,
                    validated.order(),
                    validated.items(),
                    validated.discount(),
                    validated.finalAmount(),
                    now
            );
        } catch (Exception e) {
            // 4. 재고 보상 처리
            if (stockDecreased) {
                compensateStock(validated.items(), e);
            }
            throw e;
        }
    }

    /**
     * 결제 검증 및 사전 계산 (트랜잭션 불필요)
     *
     * @return 검증된 결제 정보
     */
    private PaymentValidationResult validatePayment(PaymentCommand command, Instant now) {
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
                new ValidateDiscountCommand(command.userId(), command.couponCode(), order.getTotalAmount())
        );

        // 매핑 (쿠폰 도메인 -> 주문 도메인)
        var discount = new DiscountCalculation(
                couponResult.userCouponId(),
                couponResult.couponId(),
                couponResult.discountValue(),
                couponResult.discountAmount()
        );

        // 4. 최종 금액 계산
        int finalAmount = Math.max(0, order.getTotalAmount() - discount.discountAmount());

        return new PaymentValidationResult(order, items, discount, finalAmount);
    }

    /**
     * 결제 검증 결과 (내부 DTO)
     */
    private record PaymentValidationResult(
            Order order,
            List<OrderItem> items,
            DiscountCalculation discount,
            int finalAmount
    ) {
    }

    /**
     * 재고 보상 처리
     *
     * 결제 실패 시 차감된 재고를 복원
     */
    private void compensateStock(List<OrderItem> items, Exception originalException) {
        log.error("결제 실패, 재고 복원 시작 - items: {}, 원인: {}",
                items.size(), originalException.getMessage(), originalException);

        try {
            productStockService.increaseStocksWithLock(items);
            log.info("재고 복원 성공 - items: {}", items.size());
        } catch (Exception e) {
            log.error("⚠️ [CRITICAL] 재고 복원 실패 - items: {} - 수동 처리 필요!",
                    items, e);
            // TODO: 실무에서는 알람 발송, 수동 처리 큐에 추가, Slack 알림 등
        }
    }
}
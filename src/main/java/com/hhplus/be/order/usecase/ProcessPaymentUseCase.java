package com.hhplus.be.order.usecase;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.LockAcquisitionException;
import com.hhplus.be.coupon.service.dto.ValidateDiscountCommand;
import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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
        try {
            return executePaymentInTransaction(command, validated, now);
        } catch (Exception e) {
            // 4. 재고 보상 처리
            if (stockDecreased) {
                compensateStock(validated.items(), e);
            }
            throw e;
        }
    }

    /**
     * 주문 완료 이벤트 발행
     * <p>
     * 비동기 랭킹 업데이트를 위한 이벤트 발행
     * - @TransactionalEventListener(AFTER_COMMIT)로 트랜잭션 커밋 후 실행됨
     * - @Async로 별도 스레드에서 처리되어 주문 응답 속도에 영향 없음
     *
     * @param orderId     주문 ID
     * @param userId      사용자 ID
     * @param items       주문 항목 리스트
     * @param confirmedAt 주문 확정 시간
     */
    private void publishOrderConfirmedEvent(
            Long orderId,
            Long userId,
            List<OrderItem> items,
            Instant confirmedAt) {

        // OrderItem -> OrderItemInfo 변환
        List<OrderConfirmedEvent.OrderItemInfo> orderItemInfos = items.stream()
                .map(item -> new OrderConfirmedEvent.OrderItemInfo(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity()
                ))
                .toList();

        // 이벤트 생성 및 발행
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                orderId,
                userId,
                orderItemInfos,
                confirmedAt
        );

        eventPublisher.publishEvent(event);

        log.info("주문 완료 이벤트 발행 - orderId: {}, userId: {}, items: {}",
                orderId, userId, orderItemInfos.size());
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
     * 결제 트랜잭션 처리
     * <p>
     * 검증 및 재고 차감 이후, 실제 결제 처리를 수행
     *
     * @param command   결제 명령
     * @param validated 검증된 결제 정보
     * @param now       현재 시각
     * @return 결제 결과
     */
    @Transactional
    protected PaymentResult executePaymentInTransaction(
            PaymentCommand command,
            PaymentValidationResult validated,
            Instant now) {

        Long usedCouponId = null;

        try {
            // 5. 포인트 차감 (비관적 락)
            User user = pointService.deductPoints(command.userId(), validated.finalAmount());

            // 6. 쿠폰 사용 처리
            if (validated.discount().hasDiscount()) {
                couponService.markAsUsed(validated.discount().userCouponId());
                usedCouponId = validated.discount().userCouponId();
            }

            // 7. 주문 확정 (Order 도메인)
            orderService.confirmOrder(validated.order(), validated.finalAmount(), now);

            // 8. 할인 정보 저장 (Order 도메인)
            orderService.saveDiscountInfo(validated.order().getId(), validated.discount());

            // 9. 포인트 히스토리 기록 (Point 도메인)
            pointService.recordUseHistory(command.userId(), validated.finalAmount(), user.getBalance());

            // 10. 주문 완료 이벤트 발행 (비동기 처리용)
            publishOrderConfirmedEvent(
                    validated.order().getId(),
                    command.userId(),
                    validated.items(),
                    now
            );

            return PaymentResult.from(validated.order(), user, validated.discount().discountAmount());

        } catch (Exception e) {
            // 트랜잭션 내부 실패 시 보상 처리
            handleTransactionFailure(command.userId(), validated.finalAmount(), usedCouponId, e);
            throw e;
        }
    }

    /**
     * 트랜잭션 내부 실패 시 보상 처리
     * <p>
     * 포인트/쿠폰 복원 (트랜잭션 롤백으로 자동 처리되지만, 명시적 복원 로직 유지)
     */
    private void handleTransactionFailure(
            Long userId,
            int finalAmount,
            Long usedCouponId,
            Exception originalException) {

        log.error("트랜잭션 내부 실패 - userId: {}, finalAmount: {}, 원인: {}",
                userId, finalAmount, originalException.getMessage(), originalException);

        // 쿠폰 복원 (사용 처리된 경우)
        if (usedCouponId != null) {
            try {
                log.info("쿠폰 복원 시작 - userCouponId: {}", usedCouponId);
                couponService.restoreCoupon(usedCouponId);
                log.info("쿠폰 복원 성공 - userCouponId: {}", usedCouponId);
            } catch (Exception e) {
                log.error("쿠폰 복원 실패 - userCouponId: {}", usedCouponId, e);
            }
        }
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
package com.hhplus.be.order.service;

import com.hhplus.be.coupon.service.CouponService;
import com.hhplus.be.order.domain.model.Order;
import com.hhplus.be.order.service.dto.PaymentCommand;
import com.hhplus.be.order.service.dto.PaymentResult;
import com.hhplus.be.orderitem.domain.model.OrderItem;
import com.hhplus.be.point.service.PointService;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.usercoupon.service.dto.DiscountCalculation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 결제 트랜잭션 처리 전담 서비스
 * <p>
 * Spring AOP 프록시를 통한 @Transactional 적용을 보장하기 위해
 * ProcessPaymentUseCase에서 분리된 별도 빈
 * <p>
 * 담당 범위:
 * - 포인트 차감
 * - 쿠폰 사용 처리
 * - 주문 확정
 * - 할인 정보 저장
 * - 포인트 히스토리 기록
 * - 주문 완료 이벤트 발행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final OrderService orderService;
    private final CouponService couponService;
    private final PointService pointService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 결제 트랜잭션 처리
     * <p>
     * 이 메서드는 반드시 외부 빈에서 호출되어야 @Transactional이 적용됨
     * (Spring AOP 프록시 메커니즘)
     *
     * @param command   결제 명령
     * @param order     주문 정보
     * @param items     주문 항목
     * @param discount  할인 정보
     * @param finalAmount 최종 결제 금액
     * @param now       현재 시각
     * @return 결제 결과
     */
    @Transactional
    public PaymentResult executeInTransaction(
            PaymentCommand command,
            Order order,
            List<OrderItem> items,
            DiscountCalculation discount,
            int finalAmount,
            Instant now) {

        Long usedCouponId = null;

        try {
            // 1. 포인트 차감 (비관적 락)
            User user = pointService.deductPoints(command.userId(), finalAmount);

            // 2. 쿠폰 사용 처리
            if (discount.hasDiscount()) {
                couponService.markAsUsed(discount.userCouponId());
                usedCouponId = discount.userCouponId();
            }

            // 3. 주문 확정 (Order 도메인)
            orderService.confirmOrder(order, finalAmount, now);

            // 4. 할인 정보 저장 (Order 도메인)
            orderService.saveDiscountInfo(order.getId(), discount);

            // 5. 포인트 히스토리 기록 (Point 도메인)
            pointService.recordUseHistory(command.userId(), finalAmount, user.getBalance());

            // 6. 주문 완료 이벤트 발행 (비동기 처리용)
            OrderEventPublisher.publishOrderConfirmedEvent(
                    eventPublisher,
                    order.getId(),
                    command.userId(),
                    items,
                    now
            );

            return PaymentResult.from(order, user, discount.discountAmount());

        } catch (Exception e) {
            // 트랜잭션 내부 실패 시 보상 처리
            handleTransactionFailure(command.userId(), finalAmount, usedCouponId, e);
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
}
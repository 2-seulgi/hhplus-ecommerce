package com.hhplus.be.order.service;

import com.hhplus.be.order.domain.event.OrderConfirmedEvent;
import com.hhplus.be.orderitem.domain.model.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

/**
 * 주문 이벤트 발행 헬퍼 클래스
 * <p>
 * 주문 관련 이벤트 발행 로직을 중앙화하여 재사용성 향상
 */
@Slf4j
public class OrderEventPublisher {

    /**
     * 주문 완료 이벤트 발행
     * <p>
     * 비동기 랭킹 업데이트를 위한 이벤트 발행
     * - @TransactionalEventListener(AFTER_COMMIT)로 트랜잭션 커밋 후 실행됨
     * - @Async로 별도 스레드에서 처리되어 주문 응답 속도에 영향 없음
     *
     * @param eventPublisher Spring 이벤트 퍼블리셔
     * @param orderId        주문 ID
     * @param userId         사용자 ID
     * @param items          주문 항목 리스트
     * @param confirmedAt    주문 확정 시간
     */
    public static void publishOrderConfirmedEvent(
            ApplicationEventPublisher eventPublisher,
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
}
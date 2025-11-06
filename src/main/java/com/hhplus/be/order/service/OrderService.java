package com.hhplus.be.order.service;

import com.hhplus.be.cart.domain.CartItem;
import com.hhplus.be.cart.infrastructure.CartRepository;
import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.InvalidInputException;
import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.order.domain.Order;
import com.hhplus.be.order.infrastructure.OrderRepository;
import com.hhplus.be.order.service.dto.CreateOrderResult;
import com.hhplus.be.orderitem.domain.OrderItem;
import com.hhplus.be.orderitem.infrastructure.OrderItemRepository;
import com.hhplus.be.product.domain.Product;
import com.hhplus.be.product.infrastructure.ProductRepository;
import com.hhplus.be.user.domain.User;
import com.hhplus.be.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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
    public CreateOrderResult createFromCart(Long userId) {
        // 1) 회원 검증
        User user = users.findById(userId)
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
            Product p = products.findById(ci.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("상품 없음: " + ci.productId()));
            // 재고 0이면 주문 생성 거부 (결제 시점에 실제 차감)
            if (p.getStock() <= 0) {
                throw new BusinessException("품절 상품이 포함되어 주문을 생성할 수 없습니다: " + p.getName(), "OUT_OF_STOCK");
            }
            int unitPriceSnapshot = p.getPrice(); // 스냅샷
            totalAmount += unitPriceSnapshot * ci.quantity();
            lines.add(new Line(p, ci.quantity()));
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
}

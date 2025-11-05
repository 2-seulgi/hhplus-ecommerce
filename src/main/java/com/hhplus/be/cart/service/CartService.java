package com.hhplus.be.cart.service;

import com.hhplus.be.cart.domain.CartItem;
import com.hhplus.be.cart.infrastructure.CartRepository;
import com.hhplus.be.cart.service.dto.*;
import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.product.domain.Product;
import com.hhplus.be.product.infrastructure.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 장바구니 Service
 * API 명세 기반 구현
 */
@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    /**
     * 장바구니 담기
     * API: POST /users/{userId}/cart/items
     *
     * 동일 상품이 이미 장바구니에 있으면 수량 증가
     */
    public AddCartItemResult addCartItem(AddCartItemCommand command) {
        // 1. 상품 조회
        Product product = productRepository.findById(command.productId())
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));

        // 2. 기존 장바구니 아이템 확인
        var existingItem = cartRepository.findByUserIdAndProductId(command.userId(), command.productId());

        CartItem savedItem;
        if (existingItem.isPresent()) {
            // 기존 아이템이 있으면 수량 증가
            CartItem item = existingItem.get();
            item.changeQuantity(item.getQuantity() + command.quantity());
            savedItem = cartRepository.save(item);
        } else {
            // 신규 아이템 생성
            CartItem newItem = CartItem.create(command.userId(), command.productId(), command.quantity());
            savedItem = cartRepository.save(newItem);
        }

        // 3. 전체 장바구니 조회하여 summary 계산
        List<CartItem> allItems = cartRepository.findByUserId(command.userId());
        List<CartResult.CartItemInfo> itemInfos = allItems.stream()
                .map(item -> {
                    Product p = productRepository.findById(item.getProductId())
                            .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));
                    return CartResult.CartItemInfo.from(item, p);
                })
                .toList();

        var addedItemInfo = itemInfos.stream()
                .filter(info -> info.getCartItemId().equals(savedItem.getId()))
                .findFirst()
                .orElseThrow();

        var summary = new CartResult.CartSummary(
                itemInfos.stream().mapToInt(CartResult.CartItemInfo::getSubtotal).sum(),
                itemInfos.size()
        );

        return new AddCartItemResult(addedItemInfo, summary);
    }

    /**
     * 장바구니 조회
     * API: GET /users/{userId}/cart/items
     */
    public CartResult getCart(GetCartQuery query) {
        List<CartItem> cartItems = cartRepository.findByUserId(query.userId());

        List<CartResult.CartItemInfo> itemInfos = cartItems.stream()
                .map(item -> {
                    Product product = productRepository.findById(item.getProductId())
                            .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));
                    return CartResult.CartItemInfo.from(item, product);
                })
                .toList();

        return new CartResult(itemInfos);
    }

    /**
     * 장바구니 수량 변경
     * API: PATCH /users/{userId}/cart/items/{cartItemId}
     *
     * 수량이 0이면 자동 삭제
     */
    public CartResult updateCartItemQuantity(UpdateCartItemQuantityCommand command) {
        // 1. 장바구니 아이템 조회
        CartItem cartItem = cartRepository.findById(command.cartItemId())
                .orElseThrow(() -> new ResourceNotFoundException("장바구니 항목을 찾을 수 없습니다"));

        // 2. 권한 검증 (userId 일치 확인)
        if (!cartItem.getUserId().equals(command.userId())) {
            throw new ResourceNotFoundException("장바구니 항목을 찾을 수 없습니다");
        }

        // 3. 수량 0이면 삭제, 아니면 업데이트
        if (command.quantity() == 0) {
            cartRepository.delete(cartItem);
        } else {
            cartItem.changeQuantity(command.quantity());
            cartRepository.save(cartItem);
        }

        // 4. 전체 장바구니 조회하여 반환
        return getCart(new GetCartQuery(command.userId()));
    }

    /**
     * 장바구니 삭제
     * API: DELETE /users/{userId}/cart/items/{cartItemId}
     */
    public CartResult deleteCartItem(DeleteCartItemCommand command) {
        // 1. 장바구니 아이템 조회
        CartItem cartItem = cartRepository.findById(command.cartItemId())
                .orElseThrow(() -> new ResourceNotFoundException("장바구니 항목을 찾을 수 없습니다"));

        // 2. 권한 검증 (userId 일치 확인)
        if (!cartItem.getUserId().equals(command.userId())) {
            throw new ResourceNotFoundException("장바구니 항목을 찾을 수 없습니다");
        }

        // 3. 삭제
        cartRepository.delete(cartItem);

        // 4. 전체 장바구니 조회하여 반환
        return getCart(new GetCartQuery(command.userId()));
    }
}
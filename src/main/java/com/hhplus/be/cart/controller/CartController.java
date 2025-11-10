package com.hhplus.be.cart.controller;

import com.hhplus.be.cart.controller.dto.AddCartItemRequest;
import com.hhplus.be.cart.controller.dto.AddCartItemResponse;
import com.hhplus.be.cart.controller.dto.CartResponse;
import com.hhplus.be.cart.controller.dto.UpdateCartItemQuantityRequest;
import com.hhplus.be.cart.service.CartService;
import com.hhplus.be.cart.service.dto.AddCartItemCommand;
import com.hhplus.be.cart.service.dto.DeleteCartItemCommand;
import com.hhplus.be.cart.service.dto.GetCartQuery;
import com.hhplus.be.cart.service.dto.UpdateCartItemQuantityCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 장바구니 Controller
 * API 명세 기반 구현
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    /**
     * 장바구니 담기
     * POST /users/{userId}/cart/items
     */
    @PostMapping("/items")
    public ResponseEntity<AddCartItemResponse> addCartItem(
            @PathVariable Long userId,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        var command = new AddCartItemCommand(userId, request.productId(), request.quantity());
        var result = cartService.addCartItem(command);
        var response = AddCartItemResponse.from(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 장바구니 조회
     * GET /users/{userId}/cart/items
     */
    @GetMapping("/items")
    public ResponseEntity<CartResponse> getCart(
            @PathVariable Long userId
    ) {
        var query = new GetCartQuery(userId);
        var result = cartService.getCart(query);
        var response = CartResponse.from(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 장바구니 수량 변경
     * PATCH /users/{userId}/cart/items/{cartItemId}
     */
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> updateCartItemQuantity(
            @PathVariable Long userId,
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request
    ) {
        var command = new UpdateCartItemQuantityCommand(userId, cartItemId, request.quantity());
        var result = cartService.updateCartItemQuantity(command);
        var response = CartResponse.from(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 장바구니 삭제
     * DELETE /users/{userId}/cart/items/{cartItemId}
     */
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> deleteCartItem(
            @PathVariable Long userId,
            @PathVariable Long cartItemId
    ) {
        var command = new DeleteCartItemCommand(userId, cartItemId);
        var result = cartService.deleteCartItem(command);
        var response = CartResponse.from(result);
        return ResponseEntity.ok(response);
    }
}
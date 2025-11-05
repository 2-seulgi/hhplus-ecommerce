package com.hhplus.be.cart.service.dto;

/**
 * 장바구니 조회 Query
 * API: GET /users/{userId}/cart/items
 */
public record GetCartQuery(
        Long userId
) {
}

package com.hhplus.be.product.controller.dto;

import com.hhplus.be.product.service.dto.TopProductResult;

import java.util.List;

/**
 * 인기 상품 조회 Response
 * API: GET /products/top?period=3d&limit=5
 *
 * API 명세 응답 필드:
 * - products: [{ productId, name, price, salesCount }]
 */
public record TopProductResponse(
        List<ProductItem> products
) {
    public static TopProductResponse from(TopProductResult result) {
        List<ProductItem> items = result.products().stream()
                .map(ProductItem::from)
                .toList();
        return new TopProductResponse(items);
    }

    public record ProductItem(
            Long productId,
            String name,
            int price,
            int salesCount
    ) {
        public static ProductItem from(TopProductResult.ProductItem item) {
            return new ProductItem(
                    item.productId(),
                    item.name(),
                    item.price(),
                    item.salesCount()
            );
        }
    }
}
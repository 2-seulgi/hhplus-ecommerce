package com.hhplus.be.product.service.dto;

import com.hhplus.be.product.domain.model.Product;

import java.util.List;

/**
 * 인기 상품 조회 Result
 * API: GET /products/top?period=3d&limit=5
 */
public record TopProductResult(
        List<ProductItem> products
) {
    public record ProductItem(
            Long productId,
            String name,
            int price,
            int salesCount
    ) {
        public static ProductItem from(Product product, int salesCount) {
            return new ProductItem(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    salesCount
            );
        }
    }
}
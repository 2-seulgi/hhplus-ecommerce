package com.hhplus.be.product.service.dto;

import com.hhplus.be.product.domain.Product;

import java.util.List;

/**
 * 상품 목록 조회 Result
 * API: GET /products?page=0&size=20
 */
public record ProductListResult(
        List<ProductItem> products
) {
    public static ProductListResult from(List<Product> products) {
        return new ProductListResult(products.stream()
                .map(ProductItem::from)
                .toList());
    }

    public record ProductItem(
            Long productId,
            String name,
            String description,
            int price
    ) {
        public static ProductItem from(Product product) {
            return new ProductItem(
                    product.getProduct_id(),
                    product.getName(),
                    product.getDescription(),
                    product.getPrice()
            );
        }
    }
}
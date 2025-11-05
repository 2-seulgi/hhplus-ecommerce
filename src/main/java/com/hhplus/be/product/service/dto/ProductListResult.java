package com.hhplus.be.product.service.dto;

import com.hhplus.be.product.domain.Product;
import lombok.Getter;

import java.util.List;

/**
 * 상품 목록 조회 Result
 * API: GET /products?page=0&size=20
 */
@Getter
public class ProductListResult {
    private final List<ProductItem> products;

    public ProductListResult(List<Product> products) {
        this.products = products.stream()
                .map(ProductItem::from)
                .toList();
    }

    @Getter
    public static class ProductItem {
        private final Long productId;
        private final String name;
        private final String description;
        private final int price;

        private ProductItem(Long productId, String name, String description, int price) {
            this.productId = productId;
            this.name = name;
            this.description = description;
            this.price = price;
        }

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
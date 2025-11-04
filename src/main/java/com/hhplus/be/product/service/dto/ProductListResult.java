package com.hhplus.be.product.service.dto;

import com.hhplus.be.product.domain.Product;
import lombok.Getter;

import java.time.LocalDateTime;
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
        private final int price;
        private final int stock;
        private final LocalDateTime createdAt;

        private ProductItem(Long productId, String name, int price, int stock, LocalDateTime createdAt) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.createdAt = createdAt;
        }

        public static ProductItem from(Product product) {
            return new ProductItem(
                    product.getProduct_id(),
                    product.getName(),
                    product.getPrice(),
                    product.getStock(),
                    product.getCreatedAt()
            );
        }
    }
}
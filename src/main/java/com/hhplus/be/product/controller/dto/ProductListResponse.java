package com.hhplus.be.product.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hhplus.be.product.service.dto.ProductListResult;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 목록 조회 Response
 * API: GET /products?page=0&size=20
 */
@Getter
public class ProductListResponse {
    private final List<ProductItem> products;

    private ProductListResponse(List<ProductItem> products) {
        this.products = products;
    }

    public static ProductListResponse from(ProductListResult result) {
        List<ProductItem> items = result.getProducts().stream()
                .map(ProductItem::from)
                .toList();
        return new ProductListResponse(items);
    }

    @Getter
    public static class ProductItem {
        private final Long productId;
        private final String name;
        private final int price;
        private final int stock;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private final LocalDateTime createdAt;

        private ProductItem(Long productId, String name, int price, int stock, LocalDateTime createdAt) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.createdAt = createdAt;
        }

        public static ProductItem from(ProductListResult.ProductItem item) {
            return new ProductItem(
                    item.getProductId(),
                    item.getName(),
                    item.getPrice(),
                    item.getStock(),
                    item.getCreatedAt()
            );
        }
    }
}
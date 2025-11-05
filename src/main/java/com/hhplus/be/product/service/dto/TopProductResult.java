package com.hhplus.be.product.service.dto;

import com.hhplus.be.product.domain.Product;
import lombok.Getter;

import java.util.List;

/**
 * 인기 상품 조회 Result
 * API: GET /products/top?period=3d&limit=5
 */
@Getter
public class TopProductResult {
    private final List<ProductItem> products;

    public TopProductResult(List<ProductItem> products) {
        this.products = products;
    }

    @Getter
    public static class ProductItem {
        private final Long productId;
        private final String name;
        private final int price;
        private final int salesCount;

        private ProductItem(Long productId, String name, int price, int salesCount) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.salesCount = salesCount;
        }

        public static ProductItem from(Product product, int salesCount) {
            return new ProductItem(
                    product.getProduct_id(),
                    product.getName(),
                    product.getPrice(),
                    salesCount
            );
        }
    }
}
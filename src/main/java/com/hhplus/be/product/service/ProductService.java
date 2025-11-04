package com.hhplus.be.product.service;

import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.product.infrastructure.ProductRepository;
import com.hhplus.be.product.service.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 상품 Service
 * API 명세 기반 구현
 */
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    /**
     * 상품 목록 조회
     * API: GET /products?page=0&size=20
     */
    public ProductListResult getProducts(ProductListQuery query) {
        List<com.hhplus.be.product.domain.Product> products = productRepository.findAll();
        return new ProductListResult(products);
    }

    /**
     * 상품 상세 조회
     * API: GET /products/{productId}
     */
    public ProductDetailResult getProductDetail(ProductDetailQuery query) {
        var product = productRepository.findById(query.productId())
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));
        return new ProductDetailResult(product);
    }

    /**
     * 상품 재고 조회
     * API: GET /products/{productId}/stock
     */
    public ProductStockResult getProductStock(ProductStockQuery query) {
        var product = productRepository.findById(query.productId())
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));
        return new ProductStockResult(product);
    }
}
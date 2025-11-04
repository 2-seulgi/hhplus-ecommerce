package com.hhplus.be.product.service;

import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.product.domain.Product;
import com.hhplus.be.product.infrastructure.ProductRepository;
import com.hhplus.be.product.service.dto.ProductDetailQuery;
import com.hhplus.be.product.service.dto.ProductListQuery;
import com.hhplus.be.product.service.dto.ProductStockQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService 단위 테스트")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("상품 목록 조회 - 성공")
    void getProducts_Success() {
        // given
        var query = new ProductListQuery();
        var product1 = Product.create("무선 이어폰", "고음질", 89000, 100);
        var product2 = Product.create("스마트워치", "건강 관리", 250000, 50);
        product1.assignId(1L);
        product2.assignId(2L);

        given(productRepository.findAll()).willReturn(List.of(product1, product2));

        // when
        var result = productService.getProducts(query);

        // then
        assertThat(result.getProducts()).hasSize(2);
        assertThat(result.getProducts().get(0).getProductId()).isEqualTo(1L);
        assertThat(result.getProducts().get(0).getName()).isEqualTo("무선 이어폰");
        assertThat(result.getProducts().get(1).getProductId()).isEqualTo(2L);
        assertThat(result.getProducts().get(1).getName()).isEqualTo("스마트워치");
    }

    @Test
    @DisplayName("상품 목록 조회 - 빈 목록")
    void getProducts_EmptyList() {
        // given
        var query = new ProductListQuery();
        given(productRepository.findAll()).willReturn(List.of());

        // when
        var result = productService.getProducts(query);

        // then
        assertThat(result.getProducts()).isEmpty();
    }

    @Test
    @DisplayName("상품 상세 조회 - 성공")
    void getProductDetail_Success() {
        // given
        var productId = 1L;
        var query = new ProductDetailQuery(productId);
        var product = Product.create("무선 이어폰", "고음질 블루투스", 89000, 100);
        product.assignId(productId);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        var result = productService.getProductDetail(query);

        // then
        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getName()).isEqualTo("무선 이어폰");
        assertThat(result.getDescription()).isEqualTo("고음질 블루투스");
        assertThat(result.getPrice()).isEqualTo(89000);
        assertThat(result.getStock()).isEqualTo(100);
    }

    @Test
    @DisplayName("상품 상세 조회 - 상품을 찾을 수 없음")
    void getProductDetail_ProductNotFound() {
        // given
        var productId = 999L;
        var query = new ProductDetailQuery(productId);
        given(productRepository.findById(productId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.getProductDetail(query))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("상품을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("상품 재고 조회 - 성공")
    void getProductStock_Success() {
        // given
        var productId = 1L;
        var query = new ProductStockQuery(productId);
        var product = Product.create("무선 이어폰", "고음질 블루투스", 89000, 100);
        product.assignId(productId);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        var result = productService.getProductStock(query);

        // then
        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getStock()).isEqualTo(100);
    }

    @Test
    @DisplayName("상품 재고 조회 - 상품을 찾을 수 없음")
    void getProductStock_ProductNotFound() {
        // given
        var productId = 999L;
        var query = new ProductStockQuery(productId);
        given(productRepository.findById(productId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.getProductStock(query))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("상품을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("상품 재고 조회 - 재고가 0인 경우")
    void getProductStock_ZeroStock() {
        // given
        var productId = 1L;
        var query = new ProductStockQuery(productId);
        var product = Product.create("품절 상품", "재고 없음", 89000, 0);
        product.assignId(productId);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        var result = productService.getProductStock(query);

        // then
        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getStock()).isEqualTo(0);
    }
}
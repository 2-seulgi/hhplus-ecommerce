package com.hhplus.be.product.service;

import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.product.domain.Product;
import com.hhplus.be.product.domain.StockStatus;
import com.hhplus.be.product.infrastructure.ProductRepository;
import com.hhplus.be.product.service.dto.ProductDetailQuery;
import com.hhplus.be.product.service.dto.ProductListQuery;
import com.hhplus.be.product.service.dto.ProductStockQuery;
import com.hhplus.be.product.service.dto.TopProductQuery;
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
        var product1 = Product.create("무선 이어폰", "고음질 블루투스", 89000, 100);
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
        assertThat(result.getProducts().get(0).getDescription()).isEqualTo("고음질 블루투스");
        assertThat(result.getProducts().get(0).getPrice()).isEqualTo(89000);
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
    @DisplayName("상품 재고 조회 - 성공 (AVAILABLE)")
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
        assertThat(result.getStockStatus()).isEqualTo(StockStatus.AVAILABLE);
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
    @DisplayName("상품 재고 조회 - 재고가 0인 경우 (OUT_OF_STOCK)")
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
        assertThat(result.getStockStatus()).isEqualTo(StockStatus.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("상품 재고 조회 - 재고 부족 (LOW_STOCK)")
    void getProductStock_LowStock() {
        // given
        var productId = 1L;
        var query = new ProductStockQuery(productId);
        var product = Product.create("재고 부족 상품", "곧 품절", 89000, 5);
        product.assignId(productId);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        var result = productService.getProductStock(query);

        // then
        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getStock()).isEqualTo(5);
        assertThat(result.getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
    }

    @Test
    @DisplayName("인기 상품 조회 - 성공")
    void getTopProducts_Success() {
        // given
        var query = new TopProductQuery("3d", 3);
        var product1 = Product.create("무선 이어폰", "고음질 블루투스", 89000, 100);
        var product2 = Product.create("스마트워치", "건강 관리", 250000, 50);
        var product3 = Product.create("블루투스 스피커", "방수 휴대용", 45000, 80);
        product1.assignId(1L);
        product2.assignId(2L);
        product3.assignId(6L);

        given(productRepository.findTopProducts(3)).willReturn(List.of(product1, product2, product3));
        given(productRepository.getSalesCount(1L)).willReturn(150);
        given(productRepository.getSalesCount(2L)).willReturn(120);
        given(productRepository.getSalesCount(6L)).willReturn(95);

        // when
        var result = productService.getTopProducts(query);

        // then
        assertThat(result.getProducts()).hasSize(3);
        assertThat(result.getProducts().get(0).getProductId()).isEqualTo(1L);
        assertThat(result.getProducts().get(0).getName()).isEqualTo("무선 이어폰");
        assertThat(result.getProducts().get(0).getSalesCount()).isEqualTo(150);
        assertThat(result.getProducts().get(1).getSalesCount()).isEqualTo(120);
        assertThat(result.getProducts().get(2).getSalesCount()).isEqualTo(95);
    }

    @Test
    @DisplayName("인기 상품 조회 - 빈 목록")
    void getTopProducts_EmptyList() {
        // given
        var query = new TopProductQuery("3d", 5);
        given(productRepository.findTopProducts(5)).willReturn(List.of());

        // when
        var result = productService.getTopProducts(query);

        // then
        assertThat(result.getProducts()).isEmpty();
    }

    @Test
    @DisplayName("인기 상품 조회 - limit 파라미터가 레포로 정확히 전달된다")
    void getTopProducts_PassesLimitToRepository() {
        var query = new TopProductQuery("3d", 5);
        given(productRepository.findTopProducts(anyInt())).willReturn(List.of());
        var result = productService.getTopProducts(query);

        then(productRepository).should().findTopProducts(5);
        assertThat(result.getProducts()).isEmpty();
    }

    @Test
    @DisplayName("인기 상품 조회 - 판매량 내림차순 정렬 검증")
    void getTopProducts_VerifySalesCountDescending() {
        // given
        var query = new TopProductQuery("7d", 3);
        var product1 = createProductWithId(1L, "고판매 상품", 10000);
        var product2 = createProductWithId(2L, "중판매 상품", 30000);
        var product3 = createProductWithId(3L, "저판매 상품", 50000);

        // Repository는 이미 판매량 순으로 정렬해서 반환 (300 -> 200 -> 100)
        given(productRepository.findTopProducts(3)).willReturn(List.of(product1, product2, product3));

        given(productRepository.getSalesCount(1L)).willReturn(300);
        given(productRepository.getSalesCount(2L)).willReturn(200);
        given(productRepository.getSalesCount(3L)).willReturn(100);

        // when
        var result = productService.getTopProducts(query);

        // then
        assertThat(result.getProducts()).hasSize(3);
        assertThat(result.getProducts().get(0).getSalesCount()).isEqualTo(300);
        assertThat(result.getProducts().get(1).getSalesCount()).isEqualTo(200);
        assertThat(result.getProducts().get(2).getSalesCount()).isEqualTo(100);

        // 판매량 내림차순 정렬 확인
        for (int i = 0; i < result.getProducts().size() - 1; i++) {
            assertThat(result.getProducts().get(i).getSalesCount())
                    .isGreaterThanOrEqualTo(result.getProducts().get(i + 1).getSalesCount());
        }
    }

    @Test
    @DisplayName("인기 상품 조회 - 판매량 0인 상품 포함")
    void getTopProducts_WithZeroSalesCount() {
        // given
        var query = new TopProductQuery("3d", 2);
        var product1 = createProductWithId(1L, "판매 있는 상품", 50000);
        var product2 = createProductWithId(2L, "판매 없는 상품", 30000);

        given(productRepository.findTopProducts(2)).willReturn(List.of(product1, product2));
        given(productRepository.getSalesCount(1L)).willReturn(50);
        given(productRepository.getSalesCount(2L)).willReturn(0);

        // when
        var result = productService.getTopProducts(query);

        // then
        assertThat(result.getProducts()).hasSize(2);
        assertThat(result.getProducts().get(0).getSalesCount()).isEqualTo(50);
        assertThat(result.getProducts().get(1).getSalesCount()).isEqualTo(0);
    }

    private Product createProductWithId(Long id, String name, int price) {
        var product = Product.create(name, "설명", price, 100);
        product.assignId(id);
        return product;
    }
}
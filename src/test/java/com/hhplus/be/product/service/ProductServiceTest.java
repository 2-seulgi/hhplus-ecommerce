package com.hhplus.be.product.service;

import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.orderitem.domain.repository.OrderItemRepository;
import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.domain.model.StockStatus;
import com.hhplus.be.product.domain.repository.ProductRepository;
import com.hhplus.be.product.service.dto.*;
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
    @Mock OrderItemRepository orderItemRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("상품 목록 조회 - 성공")
    void getProducts_Success() {
        // given
        var query = new ProductListQuery();
        var product1 = Product.create("무선 이어폰", "고음질 블루투스", 89000, 100);
        assignProductId(product1, 1L);
        var product2 = Product.create("스마트워치", "건강 관리", 250000, 50);
        assignProductId(product2, 2L);

        given(productRepository.findAll()).willReturn(List.of(product1, product2));

        // when
        var result = productService.getProducts(query);

        // then
        assertThat(result.products()).hasSize(2);
        assertThat(result.products().get(0).productId()).isEqualTo(1L);
        assertThat(result.products().get(0).name()).isEqualTo("무선 이어폰");
        assertThat(result.products().get(0).description()).isEqualTo("고음질 블루투스");
        assertThat(result.products().get(0).price()).isEqualTo(89000);
        assertThat(result.products().get(1).productId()).isEqualTo(2L);
        assertThat(result.products().get(1).name()).isEqualTo("스마트워치");
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
        assertThat(result.products()).isEmpty();
    }

    @Test
    @DisplayName("상품 상세 조회 - 성공")
    void getProductDetail_Success() {
        // given
        var productId = 1L;
        var query = new ProductDetailQuery(productId);
        var product = Product.create("무선 이어폰", "고음질 블루투스", 89000, 100);
        assignProductId(product, productId);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        var result = productService.getProductDetail(query);

        // then
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.name()).isEqualTo("무선 이어폰");
        assertThat(result.description()).isEqualTo("고음질 블루투스");
        assertThat(result.price()).isEqualTo(89000);
        assertThat(result.stock()).isEqualTo(100);
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
        assignProductId(product, productId);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        var result = productService.getProductStock(query);

        // then
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.stock()).isEqualTo(100);
        assertThat(result.stockStatus()).isEqualTo(StockStatus.AVAILABLE);
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
        assignProductId(product, productId);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        var result = productService.getProductStock(query);

        // then
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.stock()).isEqualTo(0);
        assertThat(result.stockStatus()).isEqualTo(StockStatus.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("상품 재고 조회 - 재고 부족 (LOW_STOCK)")
    void getProductStock_LowStock() {
        // given
        var productId = 1L;
        var query = new ProductStockQuery(productId);
        var product = Product.create("재고 부족 상품", "곧 품절", 89000, 5);
        assignProductId(product, productId);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        var result = productService.getProductStock(query);

        // then
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.stock()).isEqualTo(5);
        assertThat(result.stockStatus()).isEqualTo(StockStatus.LOW_STOCK);
    }

    @Test
    @DisplayName("3일간 판매량 상위 5개만, 판매량 내림차순으로 반환한다 (period=3d, limit=5)")
    void getTopProducts_top5_desc_simple() {
        // given
        var salesMap = java.util.Map.of(
                1L, 10, 2L, 20, 3L, 30, 4L, 40, 5L, 50,
                6L, 60, 7L, 70, 8L, 80, 9L, 90, 10L, 100
        );
        given(orderItemRepository.countSalesByProductSince(any()))
                .willReturn(salesMap);

        // 상위 5개에 대해서만 Product 조회 스텁
        given(productRepository.findById(10L)).willReturn(Optional.of(createProductWithId(10L, "P10", 10000)));
        given(productRepository.findById(9L)).willReturn(Optional.of(createProductWithId(9L, "P9", 9000)));
        given(productRepository.findById(8L)).willReturn(Optional.of(createProductWithId(8L, "P8", 8000)));
        given(productRepository.findById(7L)).willReturn(Optional.of(createProductWithId(7L, "P7", 7000)));
        given(productRepository.findById(6L)).willReturn(Optional.of(createProductWithId(6L, "P6", 6000)));

        var query = new TopProductQuery("3d", 5);

        // when
        var result = productService.getTopProducts(query);

        // then
        // 1) limit=5 적용
        assertThat(result.products()).hasSize(5);

        // 2) 판매량 내림차순 정렬 검증: 10L, 9L, 8L, 7L, 6L
        assertThat(result.products())
                .extracting(TopProductResult.ProductItem::productId)
                .containsExactly(10L, 9L, 8L, 7L, 6L);

    }

    private Product createProductWithId(Long id, String name, int price) {
        return Product.reconstruct(id, name, "설명", price, 100, 0,
                java.time.Instant.now(), java.time.Instant.now());
    }

    private void assignProductId(Product product, Long id) {
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (Exception e) {
            throw new RuntimeException("ID 할당 실패", e);
        }
    }


}
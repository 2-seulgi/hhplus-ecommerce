package com.hhplus.be.cart.service;

import com.hhplus.be.cart.domain.CartItem;
import com.hhplus.be.cart.infrastructure.CartRepository;
import com.hhplus.be.cart.service.dto.AddCartItemCommand;
import com.hhplus.be.cart.service.dto.DeleteCartItemCommand;
import com.hhplus.be.cart.service.dto.GetCartQuery;
import com.hhplus.be.cart.service.dto.UpdateCartItemQuantityCommand;
import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.domain.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService 단위 테스트")
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    @Test
    @DisplayName("장바구니 담기 - 신규 상품")
    void addCartItem_NewItem() {
        // given
        var userId = 1L;
        var productId = 1L;
        var quantity = 2;
        var command = new AddCartItemCommand(userId, productId, quantity);

        var product = Product.create("무선 이어폰", "고음질", 89000, 100);
        assignProductId(product, productId);

        var savedItem = CartItem.create(userId, productId, quantity);
        assignCartItemId(savedItem, 1L);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(cartRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.empty());
        given(cartRepository.save(any(CartItem.class))).willReturn(savedItem);
        given(cartRepository.findByUserId(userId)).willReturn(List.of(savedItem));

        // when
        var result = cartService.addCartItem(command);

        // then
        assertThat(result.item().cartItemId()).isEqualTo(1L);
        assertThat(result.item().productId()).isEqualTo(productId);
        assertThat(result.item().quantity()).isEqualTo(quantity);
        assertThat(result.item().subtotal()).isEqualTo(89000 * 2);
        assertThat(result.summary().totalAmount()).isEqualTo(89000 * 2);
        assertThat(result.summary().itemCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("장바구니 담기 - 기존 상품 수량 증가")
    void addCartItem_ExistingItem_IncreaseQuantity() {
        // given
        var userId = 1L;
        var productId = 1L;
        var addQuantity = 2;
        var command = new AddCartItemCommand(userId, productId, addQuantity);

        var product = Product.create("무선 이어폰", "고음질", 89000, 100);
        assignProductId(product, productId);

        var existingItem = CartItem.create(userId, productId, 3);
        assignCartItemId(existingItem, 1L);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(cartRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.of(existingItem));
        given(cartRepository.save(existingItem)).willReturn(existingItem);
        given(cartRepository.findByUserId(userId)).willReturn(List.of(existingItem));

        // when
        var result = cartService.addCartItem(command);

        // then
        assertThat(result.item().quantity()).isEqualTo(5); // 3 + 2
        assertThat(result.item().subtotal()).isEqualTo(89000 * 5);
        verify(cartRepository).save(existingItem);
    }

    @Test
    @DisplayName("장바구니 담기 - 상품을 찾을 수 없음")
    void addCartItem_ProductNotFound() {
        // given
        var command = new AddCartItemCommand(1L, 999L, 2);
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.addCartItem(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("상품을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("장바구니 조회 - 성공")
    void getCart_Success() {
        // given
        var userId = 1L;
        var query = new GetCartQuery(userId);

        var product1 = Product.create("무선 이어폰", "고음질", 89000, 100);
        assignProductId(product1, 1L);
        var product2 = Product.create("스마트워치", "건강 관리", 250000, 50);
        assignProductId(product2, 2L);

        var item1 = CartItem.create(userId, 1L, 2);
        assignCartItemId(item1, 11L);
        var item2 = CartItem.create(userId, 2L, 1);
        assignCartItemId(item2, 12L);

        given(cartRepository.findByUserId(userId)).willReturn(List.of(item1, item2));
        given(productRepository.findById(1L)).willReturn(Optional.of(product1));
        given(productRepository.findById(2L)).willReturn(Optional.of(product2));

        // when
        var result = cartService.getCart(query);

        // then
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).productName()).isEqualTo("무선 이어폰");
        assertThat(result.items().get(0).quantity()).isEqualTo(2);
        assertThat(result.items().get(1).productName()).isEqualTo("스마트워치");
        assertThat(result.summary().totalAmount()).isEqualTo(89000 * 2 + 250000);
        assertThat(result.summary().itemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("장바구니 조회 - 빈 장바구니")
    void getCart_Empty() {
        // given
        var userId = 1L;
        var query = new GetCartQuery(userId);
        given(cartRepository.findByUserId(userId)).willReturn(List.of());

        // when
        var result = cartService.getCart(query);

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.summary().totalAmount()).isZero();
        assertThat(result.summary().itemCount()).isZero();
    }

    @Test
    @DisplayName("장바구니 수량 변경 - 성공")
    void updateCartItemQuantity_Success() {
        // given
        var userId = 1L;
        var cartItemId = 11L;
        var newQuantity = 5;
        var command = new UpdateCartItemQuantityCommand(userId, cartItemId, newQuantity);

        var product = Product.create("무선 이어폰", "고음질", 89000, 100);
        assignProductId(product, 1L);

        var cartItem = CartItem.create(userId, 1L, 2);
        assignCartItemId(cartItem, cartItemId);

        given(cartRepository.findById(cartItemId)).willReturn(Optional.of(cartItem));
        given(cartRepository.save(cartItem)).willReturn(cartItem);
        given(cartRepository.findByUserId(userId)).willReturn(List.of(cartItem));
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // when
        var result = cartService.updateCartItemQuantity(command);

        // then
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).quantity()).isEqualTo(newQuantity);
        verify(cartRepository).save(cartItem);
    }

    @Test
    @DisplayName("장바구니 수량 변경 - 수량 0으로 변경 시 삭제")
    void updateCartItemQuantity_ZeroQuantity_DeleteItem() {
        // given
        var userId = 1L;
        var cartItemId = 11L;
        var command = new UpdateCartItemQuantityCommand(userId, cartItemId, 0);

        var cartItem = CartItem.create(userId, 1L, 2);
        assignCartItemId(cartItem, cartItemId);

        given(cartRepository.findById(cartItemId)).willReturn(Optional.of(cartItem));
        given(cartRepository.findByUserId(userId)).willReturn(List.of());

        // when
        var result = cartService.updateCartItemQuantity(command);

        // then
        assertThat(result.items()).isEmpty();
        verify(cartRepository).delete(cartItem);
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("장바구니 수량 변경 - 권한 없음 (다른 사용자)")
    void updateCartItemQuantity_Unauthorized() {
        // given
        var userId = 1L;
        var otherUserId = 2L;
        var cartItemId = 11L;
        var command = new UpdateCartItemQuantityCommand(otherUserId, cartItemId, 5);

        var cartItem = CartItem.create(userId, 1L, 2);
        assignCartItemId(cartItem, cartItemId);

        given(cartRepository.findById(cartItemId)).willReturn(Optional.of(cartItem));

        // when & then
        assertThatThrownBy(() -> cartService.updateCartItemQuantity(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("장바구니 항목을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("장바구니 삭제 - 성공")
    void deleteCartItem_Success() {
        // given
        var userId = 1L;
        var cartItemId = 11L;
        var command = new DeleteCartItemCommand(userId, cartItemId);

        var cartItem = CartItem.create(userId, 1L, 2);
        assignCartItemId(cartItem, cartItemId);

        given(cartRepository.findById(cartItemId)).willReturn(Optional.of(cartItem));
        given(cartRepository.findByUserId(userId)).willReturn(List.of());

        // when
        var result = cartService.deleteCartItem(command);

        // then
        assertThat(result.items()).isEmpty();
        verify(cartRepository).delete(cartItem);
    }

    @Test
    @DisplayName("장바구니 삭제 - 항목을 찾을 수 없음")
    void deleteCartItem_NotFound() {
        // given
        var command = new DeleteCartItemCommand(1L, 999L);
        given(cartRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.deleteCartItem(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("장바구니 항목을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("장바구니 재고 상태 확인 - 재고 충분")
    void getCart_StockOk() {
        // given
        var userId = 1L;
        var product = Product.create("무선 이어폰", "고음질", 89000, 100);
        assignProductId(product, 1L);

        var cartItem = CartItem.create(userId, 1L, 2);
        assignCartItemId(cartItem, 11L);

        given(cartRepository.findByUserId(userId)).willReturn(List.of(cartItem));
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // when
        var result = cartService.getCart(new GetCartQuery(userId));

        // then
        assertThat(result.items().get(0).stockOk()).isTrue();
        assertThat(result.items().get(0).stock()).isEqualTo(100);
    }

    @Test
    @DisplayName("장바구니 재고 상태 확인 - 재고 부족")
    void getCart_StockNotOk() {
        // given
        var userId = 1L;
        var product = Product.create("품절 임박 상품", "재고 부족", 89000, 1);
        assignProductId(product, 1L);

        var cartItem = CartItem.create(userId, 1L, 5);
        assignCartItemId(cartItem, 11L);

        given(cartRepository.findByUserId(userId)).willReturn(List.of(cartItem));
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // when
        var result = cartService.getCart(new GetCartQuery(userId));

        // then
        assertThat(result.items().get(0).stockOk()).isFalse();
        assertThat(result.items().get(0).stock()).isEqualTo(1);
    }

    // Helper methods for ID assignment
    private void assignProductId(Product product, Long id) {
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (Exception e) {
            throw new RuntimeException("ID 할당 실패", e);
        }
    }

    private void assignCartItemId(CartItem cartItem, Long id) {
        try {
            var field = CartItem.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(cartItem, id);
        } catch (Exception e) {
            throw new RuntimeException("ID 할당 실패", e);
        }
    }
}

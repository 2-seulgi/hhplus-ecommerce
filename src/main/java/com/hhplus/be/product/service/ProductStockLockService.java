package com.hhplus.be.product.service;

import com.hhplus.be.common.annotation.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 재고 분산락 전담 서비스
 * <p>
 * Spring AOP 프록시를 통한 @DistributedLock 적용을 보장하기 위해
 * ProductStockService에서 분리된 별도 빈
 * <p>
 * 담당 범위:
 * - 단일 상품 재고 차감 (분산락 적용)
 * - 단일 상품 재고 증가 (분산락 적용)
 * <p>
 * 주의사항:
 * - 이 서비스의 메서드는 반드시 외부 빈에서 호출되어야 @DistributedLock이 적용됨
 * - ProductStockService에서 프록시 경로로 호출하여 분산락 보장
 */
@Service
@RequiredArgsConstructor
public class ProductStockLockService {

    private final ProductService productService;

    /**
     * 단일 상품 재고 차감 (분산락 적용)
     * <p>
     * 이 메서드는 반드시 외부 빈(ProductStockService)에서 호출되어야
     * Spring AOP 프록시를 통해 @DistributedLock이 적용됨
     *
     * @param productId 상품 ID
     * @param quantity  차감할 수량
     */
    @DistributedLock(key = "'product:stock:' + #productId", waitTime = 3, leaseTime = 5)
    public void decreaseStockWithLock(Long productId, int quantity) {
        productService.decreaseStock(productId, quantity);
    }

    /**
     * 단일 상품 재고 증가 (분산락 적용)
     * <p>
     * 이 메서드는 반드시 외부 빈(ProductStockService)에서 호출되어야
     * Spring AOP 프록시를 통해 @DistributedLock이 적용됨
     *
     * @param productId 상품 ID
     * @param quantity  증가할 수량
     */
    @DistributedLock(key = "'product:stock:' + #productId", waitTime = 3, leaseTime = 5)
    public void increaseStockWithLock(Long productId, int quantity) {
        productService.increaseStock(productId, quantity);
    }
}
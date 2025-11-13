package com.hhplus.be.product.infrastructure.repository;

import com.hhplus.be.product.infrastructure.entity.ProductJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 JPA Repository (Infrastructure Layer)
 * Spring Data JPA가 자동 구현
 */
public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

}

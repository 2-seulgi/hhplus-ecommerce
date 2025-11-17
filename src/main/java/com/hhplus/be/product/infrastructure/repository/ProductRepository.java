package com.hhplus.be.product.infrastructure.repository;

import com.hhplus.be.product.infrastructure.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 JPA Repository (Infrastructure Layer)
 * Spring Data JPA가 자동 구현
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

}

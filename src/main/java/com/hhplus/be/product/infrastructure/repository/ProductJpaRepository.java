package com.hhplus.be.product.infrastructure.repository;

import com.hhplus.be.product.infrastructure.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 상품 JPA Repository (Infrastructure Layer)
 * Spring Data JPA가 자동 구현
 */
public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    /**
     * Pessimistic Write Lock을 사용한 상품 조회
     * SELECT ... FOR UPDATE
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

}

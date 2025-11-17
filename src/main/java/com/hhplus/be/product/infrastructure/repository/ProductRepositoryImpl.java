package com.hhplus.be.product.infrastructure.repository;

import com.hhplus.be.product.domain.model.Product;
import com.hhplus.be.product.infrastructure.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ProductRepository 구현체 (Infrastructure Layer)
 * Domain ↔ JPA Entity 변환 처리
 */
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements com.hhplus.be.product.domain.repository.ProductRepository {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    public Optional<Product> findById(Long productId) {
        return productRepository.findById(productId)
                .map(productMapper::toDomain);
    }

    @Override
    public List<Product> findAll() {
        return productRepository.findAll().stream()
                .map(productMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Product save(Product product) {
        var entity = productMapper.toEntity(product);
        var savedEntity = productRepository.save(entity);
        return productMapper.toDomain(savedEntity);
    }

    @Override
    public void deleteAll() {
        productRepository.deleteAll();
    }
}
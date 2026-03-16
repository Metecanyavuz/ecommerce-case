package com.mete.ecommerce.stock.repository;

import com.mete.ecommerce.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock,Long> {
    Optional<Stock> findByProductId(Long productId);
    boolean existsByProductId(Long productId);
}

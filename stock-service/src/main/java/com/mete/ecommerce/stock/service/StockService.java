package com.mete.ecommerce.stock.service;

import com.mete.ecommerce.stock.dto.StockRequest;
import com.mete.ecommerce.stock.dto.StockResponse;
import com.mete.ecommerce.stock.entity.Stock;
import com.mete.ecommerce.stock.exception.InsufficientStockException;
import com.mete.ecommerce.stock.exception.StockNotFoundException;
import com.mete.ecommerce.stock.repository.StockRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    public StockResponse getByProductId(Long productId){
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));
        return new StockResponse(stock);
    }

    @Transactional
    public StockResponse increase(StockRequest request){
        Stock stock = stockRepository.findByProductId(request.getProductId())
                .orElseGet(() -> Stock.builder()
                        .productId(request.getProductId())
                        .quantity(0)
                        .build());
        stock.setQuantity(stock.getQuantity() + request.getQuantity());

        return new StockResponse(stockRepository.save(stock));
    }

    @Transactional
    public StockResponse decrease(StockRequest request) {
        Stock stock = stockRepository.findByProductId(request.getProductId())
                .orElseThrow(() -> new StockNotFoundException(request.getProductId()));

        if (stock.getQuantity() < request.getQuantity()) {
            throw new InsufficientStockException(
                    request.getProductId(),
                    stock.getQuantity(),
                    request.getQuantity()
            );
        }

        stock.setQuantity(stock.getQuantity() - request.getQuantity());
        return new StockResponse(stockRepository.save(stock));
    }
}

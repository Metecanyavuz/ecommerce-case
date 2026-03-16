package com.mete.ecommerce.stock.exception;

public class InsufficientStockException extends RuntimeException{
    public InsufficientStockException(Long productId , int available, int requested){
        super(String.format(
                "Insufficient stock. Product ID: %d | Available: %d | Requested: %d",
                productId, available, requested
        ));
    }
}

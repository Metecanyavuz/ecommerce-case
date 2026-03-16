package com.mete.ecommerce.stock.exception;

public class StockNotFoundException extends RuntimeException {
    public StockNotFoundException(Long productId) {
        super("Stock record not found. Product ID: " + productId);
    }
}

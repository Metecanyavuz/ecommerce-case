package com.mete.ecommerce.stock.dto;

import com.mete.ecommerce.stock.entity.Stock;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class StockResponse {

    private final long id;
    private final long productId;
    private final int quantity;
    private final LocalDateTime updatedAt;

    public StockResponse(Stock stock) {
        this.id = stock.getId();
        this.productId = stock.getProductId();
        this.quantity = stock.getQuantity();
        this.updatedAt = stock.getUpdatedAt();
    }
}

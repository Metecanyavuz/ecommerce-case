package com.mete.ecommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// DTO to be sent to Stock Service via Feign
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class StockRequest {
    private Long productId;
    private Integer quantity;
}

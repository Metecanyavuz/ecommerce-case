package com.mete.ecommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Feign üzerinden Stock Service'e gönderilecek DTO
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class StockRequest {
    private Long productId;
    private Integer quantity;
}

package com.mete.ecommerce.admin.dto.stock;

import lombok.Getter; import lombok.Setter;
@Getter @Setter
public class StockRequest {
    private Long productId;
    private Integer quantity;
}

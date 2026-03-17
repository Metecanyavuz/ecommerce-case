package com.mete.ecommerce.admin.dto.stock;

import lombok.Getter; import lombok.Setter;
import java.time.LocalDateTime;
@Getter @Setter
public class StockDto {
    private Long id;
    private Long productId;
    private Integer quantity;
    private LocalDateTime updatedAt;
}
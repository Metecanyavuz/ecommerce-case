package com.mete.ecommerce.admin.dto.order;

import lombok.Getter; import lombok.Setter;
import java.time.LocalDateTime;
@Getter @Setter
public class OrderDto {
    private Long id;
    private Long customerId;
    private Long productId;
    private Integer quantity;
    private String status;
    private LocalDateTime createdAt;
}
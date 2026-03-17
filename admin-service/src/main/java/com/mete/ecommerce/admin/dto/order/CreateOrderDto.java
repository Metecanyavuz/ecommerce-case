package com.mete.ecommerce.admin.dto.order;

import lombok.Getter; import lombok.Setter;
@Getter @Setter
public class CreateOrderDto {
    private Long customerId;
    private Long productId;
    private Integer quantity;
}
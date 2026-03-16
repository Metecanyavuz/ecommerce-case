package com.mete.ecommerce.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter@Setter
public class CreateOrderRequest {

    @NotNull(message = "Customer ID cannot be null!")
    private Long customerId;

    @NotNull(message = "Product ID cannot be null!")
    private Long productId;

    @NotNull
    @Min(value = 1, message = "Quantity must be at least 1!")
    private Integer quantity;

}

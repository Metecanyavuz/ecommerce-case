package com.mete.ecommerce.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Data
@Getter@Setter
public class StockRequest {

    @NotNull(message = "Product id cannot be null!")
    private long productId;

    @NotNull
    @Min(value = 0, message = "Quantity must be greater than 0")
    private int quantity;


}

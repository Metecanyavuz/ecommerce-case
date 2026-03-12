package com.mete.ecommerce.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter@Setter
public class UpdateProductRequest {
    private String name;
    private String description;

    @DecimalMin(value = "0.0" , inclusive = false)
    private BigDecimal price;

    private String category;
}

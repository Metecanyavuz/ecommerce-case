package com.mete.ecommerce.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateProductRequest {

    @NotBlank(message = "Product name cannot be empty!")
    private String name;

    private String description;

    @NotNull(message = "Price cannot be empty!")
    @DecimalMin(value = "0.0" , inclusive = false, message = "Price must be bigger than 0!")
    private BigDecimal price;

    private String category;

}

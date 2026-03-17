package com.mete.ecommerce.admin.dto.product;

import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
import java.math.BigDecimal;
@Getter @Setter
public class CreateProductDto {
    @NotBlank private String name;
    private String description;
    @NotNull @DecimalMin("0.0") private BigDecimal price;
    private String category;
}

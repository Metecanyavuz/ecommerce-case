package com.mete.ecommerce.admin.dto.product;
import lombok.Getter; import lombok.Setter;
import java.math.BigDecimal; import java.time.LocalDateTime;
@Getter @Setter
public class ProductDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private LocalDateTime createdAt;
}
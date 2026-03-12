package com.mete.ecommerce.product.dto;

import com.mete.ecommerce.product.entity.Product;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class ProductResponse {

    private final Long id;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final String category;
    private final LocalDateTime createdAt;

    public ProductResponse(Product product) {
        this.id = product.getId();
        this.name = product.getName();
        this.description = product.getDescription();
        this.price = product.getPrice();
        this.category = product.getCategory();
        this.createdAt = product.getCreatedAt();
    }

}

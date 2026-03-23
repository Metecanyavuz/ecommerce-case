package com.mete.ecommerce.product.dto;

import com.mete.ecommerce.product.entity.Product;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProductResponse {

    private  Long id;
    private  String name;
    private  String description;
    private  BigDecimal price;
    private  String category;
    private  LocalDateTime createdAt;

    public ProductResponse(Product product) {
        this.id = product.getId();
        this.name = product.getName();
        this.description = product.getDescription();
        this.price = product.getPrice();
        this.category = product.getCategory();
        this.createdAt = product.getCreatedAt();
    }

    // Test için no-args constructor
    public ProductResponse() {}

}

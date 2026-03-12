package com.mete.ecommerce.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products", schema = "products")
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT") // annotation in JPA/Hibernate is used to explicitly specify that the corresponding entity field should be mapped to a database column with the TEXT data type.
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    private String category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }





}

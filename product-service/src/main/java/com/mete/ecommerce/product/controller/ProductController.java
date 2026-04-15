package com.mete.ecommerce.product.controller;

import com.mete.ecommerce.product.cache.ProductCacheService;
import com.mete.ecommerce.product.dto.CreateProductRequest;
import com.mete.ecommerce.product.dto.ProductResponse;
import com.mete.ecommerce.product.dto.UpdateProductRequest;
import com.mete.ecommerce.product.entity.Product;
import com.mete.ecommerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Products", description = "Product catalog management — CRUD operations")
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService      productService;
    private final ProductCacheService productCacheService;

    @Operation(summary = "List all products", description = "Returns a list of all products in the catalog.")
    @ApiResponse(responseCode = "200", description = "List returned successfully")
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @Operation(
        summary = "List all products — Redis JSON cache",
        description = "Cache-aside: Redis'te JSON bytes olarak saklanır. Cache miss → PostgreSQL → Redis. TTL: 60s."
    )
    @ApiResponse(responseCode = "200", description = "List returned (from cache or DB)")
    @GetMapping("/cached-json")
    public ResponseEntity<List<ProductResponse>> getAllProductsCachedJson() {
        return ResponseEntity.ok(productCacheService.getAllProductsJson());
    }

    @Operation(
        summary = "List all products — Redis Protobuf cache",
        description = "Cache-aside: Redis'te Protobuf bytes olarak saklanır. Cache miss → PostgreSQL → Redis. TTL: 60s."
    )
    @ApiResponse(responseCode = "200", description = "List returned (from cache or DB)")
    @GetMapping("/cached-proto")
    public ResponseEntity<List<ProductResponse>> getAllProductsCachedProto() {
        return ResponseEntity.ok(productCacheService.getAllProductsProtobuf());
    }

    @Operation(summary = "Get product by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product found"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @Parameter(description = "Product ID", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @Operation(summary = "Create a new product")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Product created"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(request));
    }

    @Operation(summary = "Update product by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product updated"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @Parameter(description = "Product ID", required = true) @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @Operation(summary = "Delete product by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Product deleted"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Product ID", required = true) @PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}

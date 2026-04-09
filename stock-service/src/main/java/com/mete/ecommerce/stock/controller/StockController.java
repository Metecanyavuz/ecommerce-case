package com.mete.ecommerce.stock.controller;

import com.mete.ecommerce.stock.dto.StockRequest;
import com.mete.ecommerce.stock.dto.StockResponse;
import com.mete.ecommerce.stock.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Stock", description = "Inventory management — query and adjust stock levels per product")
@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @Operation(summary = "Get stock by product ID", description = "Returns the current stock level for a specific product.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock record found"),
        @ApiResponse(responseCode = "404", description = "No stock record found for this product")
    })
    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> getByProductId(
            @Parameter(description = "Product ID", required = true) @PathVariable Long productId) {
        return ResponseEntity.ok(stockService.getByProductId(productId));
    }

    @Operation(summary = "Increase stock", description = "Adds the specified quantity to the product's stock level.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock increased successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @PostMapping("/increase")
    public ResponseEntity<StockResponse> increase(@Valid @RequestBody StockRequest request) {
        return ResponseEntity.ok(stockService.increase(request));
    }

    @Operation(summary = "Decrease stock", description = "Subtracts the specified quantity from the product's stock level. Fails if stock would go below zero.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock decreased successfully"),
        @ApiResponse(responseCode = "400", description = "Insufficient stock or validation error"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @PostMapping("/decrease")
    public ResponseEntity<StockResponse> decrease(@Valid @RequestBody StockRequest request) {
        return ResponseEntity.ok(stockService.decrease(request));
    }
}

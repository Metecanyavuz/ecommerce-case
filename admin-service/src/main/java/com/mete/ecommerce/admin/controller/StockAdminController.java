package com.mete.ecommerce.admin.controller;

import com.mete.ecommerce.admin.service.StockAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Stock", description = "Admin panel actions to increase or decrease product stock levels")
@Controller
@RequestMapping("/admin/stocks")
@RequiredArgsConstructor
public class StockAdminController {
    private final StockAdminService stockService;

    @Operation(summary = "Increase stock", description = "Adds the given quantity to the product's stock and redirects to the stock detail page.")
    @ApiResponse(responseCode = "302", description = "Redirect to /admin/products/{productId}/stock")
    @PostMapping("/{productId}/increase")
    public String increase(
            @Parameter(description = "Product ID") @PathVariable Long productId,
            @Parameter(description = "Quantity to add") @RequestParam Integer quantity) {
        stockService.increase(productId, quantity);
        return "redirect:/admin/products/" + productId + "/stock";
    }

    @Operation(summary = "Decrease stock", description = "Subtracts the given quantity from the product's stock and redirects to the stock detail page.")
    @ApiResponse(responseCode = "302", description = "Redirect to /admin/products/{productId}/stock")
    @PostMapping("/{productId}/decrease")
    public String decrease(
            @Parameter(description = "Product ID") @PathVariable Long productId,
            @Parameter(description = "Quantity to subtract") @RequestParam Integer quantity) {
        stockService.decrease(productId, quantity);
        return "redirect:/admin/products/" + productId + "/stock";
    }
}

package com.mete.ecommerce.admin.controller;

import com.mete.ecommerce.admin.dto.product.CreateProductDto;
import com.mete.ecommerce.admin.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Products", description = "Admin panel views for listing, creating, editing, deleting products and viewing stock")
@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class ProductAdminController {
    private final ProductAdminService productService;
    private final StockAdminService stockService;

    @Operation(summary = "List all products")
    @ApiResponse(responseCode = "200", description = "Product list page rendered")
    @GetMapping
    public String list(Model model) {
        model.addAttribute("products", productService.getAll());
        return "products/list";
    }

    @Operation(summary = "New product form")
    @ApiResponse(responseCode = "200", description = "Create product form rendered")
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("product", new CreateProductDto());
        return "products/form";
    }

    @Operation(summary = "Create product", description = "Submits the new product form and redirects to the product list.")
    @ApiResponse(responseCode = "302", description = "Redirect to /admin/products on success")
    @PostMapping("/new")
    public String create(@Valid @ModelAttribute CreateProductDto dto) {
        productService.create(dto);
        return "redirect:/admin/products";
    }

    @Operation(summary = "Edit product form")
    @ApiResponse(responseCode = "200", description = "Edit product form rendered")
    @GetMapping("/{id}/edit")
    public String editForm(
            @Parameter(description = "Product ID") @PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getById(id));
        return "products/form";
    }

    @Operation(summary = "Update product")
    @ApiResponse(responseCode = "302", description = "Redirect to /admin/products on success")
    @PostMapping("/{id}/edit")
    public String update(
            @Parameter(description = "Product ID") @PathVariable Long id,
            @Valid @ModelAttribute CreateProductDto dto) {
        productService.update(id, dto);
        return "redirect:/admin/products";
    }

    @Operation(summary = "Delete product")
    @ApiResponse(responseCode = "302", description = "Redirect to /admin/products after deletion")
    @PostMapping("/{id}/delete")
    public String delete(
            @Parameter(description = "Product ID") @PathVariable Long id) {
        productService.delete(id);
        return "redirect:/admin/products";
    }

    @Operation(summary = "View product stock", description = "Shows the current stock level for the given product.")
    @ApiResponse(responseCode = "200", description = "Stock detail page rendered")
    @GetMapping("/{id}/stock")
    public String stock(
            @Parameter(description = "Product ID") @PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getById(id));
        model.addAttribute("stock", stockService.getByProductId(id));
        return "stocks/detail";
    }
}

package com.mete.ecommerce.admin.controller;

import com.mete.ecommerce.admin.dto.product.CreateProductDto;
import com.mete.ecommerce.admin.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller @RequestMapping("/admin/products") @RequiredArgsConstructor
public class ProductAdminController {
    private final ProductAdminService productService;
    private final StockAdminService stockService;
    @GetMapping
    public String list(Model model) {
        model.addAttribute("products", productService.getAll());
        return "products/list";
    }
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("product", new CreateProductDto());
        return "products/form";
    }
    @PostMapping("/new")
    public String create(@Valid @ModelAttribute CreateProductDto dto) {
        productService.create(dto);
        return "redirect:/admin/products";
    }
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getById(id));
        return "products/form";
    }
    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute
    CreateProductDto dto) {
        productService.update(id, dto);
        return "redirect:/admin/products";
    }
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        productService.delete(id);
        return "redirect:/admin/products";
    }
    @GetMapping("/{id}/stock")
    public String stock(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getById(id));
        model.addAttribute("stock", stockService.getByProductId(id));
        return "stocks/detail";
    }
}
package com.mete.ecommerce.admin.controller;

import com.mete.ecommerce.admin.service.StockAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
@Controller @RequestMapping("/admin/stocks") @RequiredArgsConstructor
public class StockAdminController {
    private final StockAdminService stockService;
    @PostMapping("/{productId}/increase")
    public String increase(@PathVariable Long productId, @RequestParam Integer
            quantity) {
        stockService.increase(productId, quantity);
        return "redirect:/admin/products/" + productId + "/stock";
    }
    @PostMapping("/{productId}/decrease")
    public String decrease(@PathVariable Long productId, @RequestParam Integer
            quantity) {
        stockService.decrease(productId, quantity);
        return "redirect:/admin/products/" + productId + "/stock";
    }
}
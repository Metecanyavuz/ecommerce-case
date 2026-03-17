package com.mete.ecommerce.admin.controller;

import com.mete.ecommerce.admin.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@Controller @RequestMapping("/admin") @RequiredArgsConstructor
public class DashboardController {
    private final ProductAdminService productService;
    private final CustomerAdminService customerService;
    private final OrderAdminService orderService;
    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        try {
            var products = productService.getAll();
            var customers = customerService.getAll();
            var orders = orderService.getAll();
            model.addAttribute("productCount", products != null ?
                    products.size() : 0);
            model.addAttribute("customerCount", customers != null ?
                    customers.size() : 0);
            model.addAttribute("orderCount", orders != null ?
                    orders.size() : 0);
            model.addAttribute("recentOrders",
                    orders != null ? orders.stream().limit(5).toList() : List.of());
        } catch (Exception e) {
            model.addAttribute("error", "Servislerden veri alınamadı: " +
                    e.getMessage());
        }
        return "dashboard";
    }
}
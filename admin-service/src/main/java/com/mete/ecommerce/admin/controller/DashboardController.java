package com.mete.ecommerce.admin.controller;

import com.mete.ecommerce.admin.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "Dashboard", description = "Admin dashboard — summary statistics for products, customers and orders")
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class DashboardController {
    private final ProductAdminService productService;
    private final CustomerAdminService customerService;
    private final OrderAdminService orderService;

    @Operation(summary = "Admin dashboard", description = "Returns the dashboard view with product, customer and order counts and the 5 most recent orders.")
    @ApiResponse(responseCode = "200", description = "Dashboard page rendered")
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

package com.mete.ecommerce.admin.controller;

import com.mete.ecommerce.admin.dto.order.CreateOrderDto;
import com.mete.ecommerce.admin.service.OrderAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Orders", description = "Admin panel views for listing, viewing, creating, and updating order status")
@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {
    private final OrderAdminService orderService;

    @Operation(summary = "List all orders")
    @ApiResponse(responseCode = "200", description = "Order list page rendered")
    @GetMapping
    public String list(Model model) {
        model.addAttribute("orders", orderService.getAll());
        return "orders/list";
    }

    @Operation(summary = "Order detail")
    @ApiResponse(responseCode = "200", description = "Order detail page rendered")
    @GetMapping("/{id}")
    public String detail(
            @Parameter(description = "Order ID") @PathVariable Long id, Model model) {
        model.addAttribute("order", orderService.getById(id));
        model.addAttribute("statuses",
                new String[]{"CREATED", "PAYMENT_PENDING", "PAID", "SHIPPED", "CANCELLED"});
        return "orders/detail";
    }

    @Operation(summary = "New order form")
    @ApiResponse(responseCode = "200", description = "Create order form rendered")
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("order", new CreateOrderDto());
        return "orders/form";
    }

    @Operation(summary = "Create order", description = "Submits the new order form and redirects to the order list.")
    @ApiResponse(responseCode = "302", description = "Redirect to /admin/orders on success")
    @PostMapping("/new")
    public String create(@ModelAttribute CreateOrderDto dto) {
        orderService.createOrder(dto);
        return "redirect:/admin/orders";
    }

    @Operation(summary = "Update order status", description = "Updates order status (CREATED, PAYMENT_PENDING, PAID, SHIPPED, CANCELLED).")
    @ApiResponse(responseCode = "302", description = "Redirect to order detail on success")
    @PostMapping("/{id}/status")
    public String updateStatus(
            @Parameter(description = "Order ID") @PathVariable Long id,
            @Parameter(description = "New status") @RequestParam String status) {
        orderService.updateStatus(id, status);
        return "redirect:/admin/orders/" + id;
    }
}

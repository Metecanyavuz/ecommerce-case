package com.mete.ecommerce.admin.controller;

import com.mete.ecommerce.admin.dto.order.CreateOrderDto;
import com.mete.ecommerce.admin.service.OrderAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {
    private final OrderAdminService orderService;
    @GetMapping
    public String list(Model model) {
        model.addAttribute("orders", orderService.getAll());
        return "orders/list";
    }
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("order", orderService.getById(id));
        model.addAttribute("statuses",
                new String[]{"CREATED","PAYMENT_PENDING","PAID","SHIPPED","CANCELLED"});
        return "orders/detail";
    }
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("order", new CreateOrderDto());
        return "orders/form";
    }
    @PostMapping("/new")
    public String create(@ModelAttribute CreateOrderDto dto) {
        orderService.createOrder(dto);
        return "redirect:/admin/orders";
    }
    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam String status) {
        orderService.updateStatus(id, status);
        return "redirect:/admin/orders/" + id;
    }
}
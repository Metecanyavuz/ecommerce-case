package com.mete.ecommerce.admin.controller;

import com.mete.ecommerce.admin.dto.customer.CreateCustomerDto;
import com.mete.ecommerce.admin.service.CustomerAdminService;
import com.mete.ecommerce.admin.service.CustomerAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller
@RequestMapping("/admin/customers")
@RequiredArgsConstructor
public class CustomerAdminController {
    private final CustomerAdminService customerService;
    @GetMapping
    public String list(Model model) {
        model.addAttribute("customers", customerService.getAll());
        return "customers/list";
    }
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("customer", customerService.getById(id));
        return "customers/detail";
    }
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("customer", new CreateCustomerDto());
        return "customers/form";
    }
    @PostMapping("/new")
    public String create(@ModelAttribute CreateCustomerDto dto) {
        customerService.createCustomer(dto);
        return "redirect:/admin/customers";
    }
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        customerService.delete(id);
        return "redirect:/admin/customers";
    }
}
package com.mete.ecommerce.admin.controller;

import com.mete.ecommerce.admin.dto.customer.CreateCustomerDto;
import com.mete.ecommerce.admin.service.CustomerAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Customers", description = "Admin panel views for listing, viewing, creating, and deleting customers")
@Controller
@RequestMapping("/admin/customers")
@RequiredArgsConstructor
public class CustomerAdminController {
    private final CustomerAdminService customerService;

    @Operation(summary = "List all customers")
    @ApiResponse(responseCode = "200", description = "Customer list page rendered")
    @GetMapping
    public String list(Model model) {
        model.addAttribute("customers", customerService.getAll());
        return "customers/list";
    }

    @Operation(summary = "Customer detail")
    @ApiResponse(responseCode = "200", description = "Customer detail page rendered")
    @GetMapping("/{id}")
    public String detail(
            @Parameter(description = "Customer ID") @PathVariable Long id, Model model) {
        model.addAttribute("customer", customerService.getById(id));
        return "customers/detail";
    }

    @Operation(summary = "New customer form")
    @ApiResponse(responseCode = "200", description = "Create customer form rendered")
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("customer", new CreateCustomerDto());
        return "customers/form";
    }

    @Operation(summary = "Create customer", description = "Submits the new customer form and redirects to the customer list.")
    @ApiResponse(responseCode = "302", description = "Redirect to /admin/customers on success")
    @PostMapping("/new")
    public String create(@ModelAttribute CreateCustomerDto dto) {
        customerService.createCustomer(dto);
        return "redirect:/admin/customers";
    }

    @Operation(summary = "Delete customer")
    @ApiResponse(responseCode = "302", description = "Redirect to /admin/customers after deletion")
    @PostMapping("/{id}/delete")
    public String delete(
            @Parameter(description = "Customer ID") @PathVariable Long id) {
        customerService.delete(id);
        return "redirect:/admin/customers";
    }
}

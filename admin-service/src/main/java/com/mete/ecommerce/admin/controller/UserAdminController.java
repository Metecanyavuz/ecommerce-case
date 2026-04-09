package com.mete.ecommerce.admin.controller;

import com.mete.ecommerce.admin.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Users", description = "Admin panel view for listing registered users from the Auth Service")
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserAdminController {
    private final UserAdminService userService;

    @Operation(summary = "List all users", description = "Fetches all registered users from the Auth Service and renders the user list page.")
    @ApiResponse(responseCode = "200", description = "User list page rendered")
    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.getAll());
        return "users/list";
    }
}

package com.mete.ecommerce.admin.controller;

import com.mete.ecommerce.admin.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller @RequestMapping("/admin/users") @RequiredArgsConstructor
public class UserAdminController {
    private final UserAdminService userService;
    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.getAll());
        return "users/list";
    }
}

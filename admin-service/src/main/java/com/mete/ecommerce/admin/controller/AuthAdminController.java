package com.mete.ecommerce.admin.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Hidden
@Controller
public class AuthAdminController {
    @GetMapping("/admin/login")
    public String login() { return "login"; }
}

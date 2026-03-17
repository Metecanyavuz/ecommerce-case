package com.mete.ecommerce.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class AuthAdminController {
    @GetMapping("/admin/login")
    public String login() { return "login"; }
}
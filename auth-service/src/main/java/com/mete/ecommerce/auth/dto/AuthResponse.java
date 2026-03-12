package com.mete.ecommerce.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class AuthResponse {
    private String token;
    private String email;
    private String role;
}

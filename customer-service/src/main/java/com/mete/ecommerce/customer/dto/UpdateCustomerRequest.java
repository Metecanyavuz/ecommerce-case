package com.mete.ecommerce.customer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter@Setter
public class UpdateCustomerRequest {
    @NotBlank(message = "Name cannot be empty!")
    private String name;

    @NotBlank(message = "Name cannot be empty!")
    private String surname;

    @NotBlank(message = "Invalid email!")
    private String email;

    private String phone;
    private String address;


}

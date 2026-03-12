package com.mete.ecommerce.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter@Setter
public class CreateCustomerRequest {

    @NotBlank(message = "Name cannot be empty!")
    private String name;

    @NotBlank(message = "Name cannot be empty!")
    private String surname;

    @NotBlank(message = "Invalid email!")
    private String email;

    private String phone;
    private String address;
}

package com.mete.ecommerce.customer.dto;

import com.mete.ecommerce.customer.entity.Customer;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CustomerResponse {
    private final Long id;
    private final String name;
    private final String surname;
    private final String email;
    private final String phone;
    private final String address;
    private final LocalDateTime createdAt;


    public CustomerResponse(Customer customer){
        this.id = customer.getId();
        this.name = customer.getName();
        this.surname = customer.getSurname();
        this.email = customer.getEmail();
        this.phone = customer.getPhone();
        this.address = customer.getAddress();
        this.createdAt = customer.getCreatedAt();
    }
}

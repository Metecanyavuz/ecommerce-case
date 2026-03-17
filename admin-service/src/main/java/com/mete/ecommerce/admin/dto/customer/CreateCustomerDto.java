package com.mete.ecommerce.admin.dto.customer;

import lombok.Getter; import lombok.Setter;
@Getter @Setter
public class CreateCustomerDto {
    private String name;
    private String surname;
    private String email;
    private String phone;
    private String address;
}
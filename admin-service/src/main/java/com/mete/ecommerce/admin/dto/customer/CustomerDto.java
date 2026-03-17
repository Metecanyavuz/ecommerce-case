package com.mete.ecommerce.admin.dto.customer;

import lombok.Getter; import lombok.Setter;
import java.time.LocalDateTime;
@Getter @Setter
public class CustomerDto {
    private Long id;
    private String name;
    private String surname;
    private String email;
    private String phone;
    private String address;
    private LocalDateTime createdAt;
}
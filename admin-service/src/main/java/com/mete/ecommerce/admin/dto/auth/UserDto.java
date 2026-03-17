package com.mete.ecommerce.admin.dto.auth;

import lombok.Getter; import lombok.Setter;
import java.time.LocalDateTime;
@Getter @Setter
public class UserDto {
    private Long id;
    private String email;
    private String role;
    private LocalDateTime createdAt;
}
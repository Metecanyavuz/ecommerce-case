package com.mete.ecommerce.admin.service;

import com.mete.ecommerce.admin.config.ServiceProperties;
import com.mete.ecommerce.admin.dto.auth.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;

@Service @RequiredArgsConstructor
public class UserAdminService {
    private final RestTemplate restTemplate;
    private final ServiceProperties props;
    private final TokenService tokenService;
    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", tokenService.bearerHeader());
        return h;
    }
    public List<UserDto> getAll() {
        return restTemplate.exchange(props.getAuth().getUrl() + "/auth/users",
                HttpMethod.GET, new HttpEntity<>(headers()),
                new ParameterizedTypeReference<List<UserDto>>() {}).getBody();
    }
}
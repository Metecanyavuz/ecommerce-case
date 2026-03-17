package com.mete.ecommerce.admin.service;

import com.mete.ecommerce.admin.config.ServiceProperties;
import com.mete.ecommerce.admin.dto.customer.CreateCustomerDto;
import com.mete.ecommerce.admin.dto.customer.CustomerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;

@Service @RequiredArgsConstructor
public class CustomerAdminService {

    private final RestTemplate restTemplate;
    private final ServiceProperties props;
    private final TokenService tokenService;
    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", tokenService.bearerHeader());
        return h;
    }

    public List<CustomerDto> getAll() {
        return restTemplate.exchange(props.getCustomer().getUrl() + "/customers",
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                new ParameterizedTypeReference<List<CustomerDto>>() {}).getBody();
    }

    public CustomerDto getById(Long id) {
        return restTemplate.exchange(props.getCustomer().getUrl() + "/customers/" + id,
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                CustomerDto.class).getBody();
    }

    public void delete(Long id) {
        restTemplate.exchange(props.getCustomer().getUrl() + "/customers/" + id,
                HttpMethod.DELETE, new HttpEntity<>(headers()), Void.class);
    }

    public void createCustomer(CreateCustomerDto dto) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", tokenService.bearerHeader());
        h.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(props.getCustomer().getUrl() + "/customers",
                HttpMethod.POST, new HttpEntity<>(dto, h), CustomerDto.class);
    }
}
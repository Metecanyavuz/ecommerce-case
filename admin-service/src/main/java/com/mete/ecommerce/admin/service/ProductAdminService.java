package com.mete.ecommerce.admin.service;

import com.mete.ecommerce.admin.config.ServiceProperties;
import com.mete.ecommerce.admin.dto.product.CreateProductDto;
import com.mete.ecommerce.admin.dto.product.ProductDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
@Service @RequiredArgsConstructor
public class ProductAdminService {
    private final RestTemplate restTemplate;
    private final ServiceProperties props;
    private final TokenService tokenService;
    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", tokenService.bearerHeader());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
    public List<ProductDto> getAll() {
        return restTemplate.exchange(props.getProduct().getUrl() + "/products",
                HttpMethod.GET, new HttpEntity<>(headers()),
                new ParameterizedTypeReference<List<ProductDto>>() {}).getBody();
    }
    public ProductDto getById(Long id) {
        return restTemplate.exchange(props.getProduct().getUrl() + "/products/"
                        + id,
                HttpMethod.GET, new HttpEntity<>(headers()),
                ProductDto.class).getBody();
    }
    public void create(CreateProductDto dto) {
        restTemplate.exchange(props.getProduct().getUrl() + "/products",
                HttpMethod.POST, new HttpEntity<>(dto, headers()),
                ProductDto.class);
    }
    public void update(Long id, CreateProductDto dto) {
        restTemplate.exchange(props.getProduct().getUrl() + "/products/" + id,
                HttpMethod.PUT, new HttpEntity<>(dto, headers()), ProductDto.class);
    }
    public void delete(Long id) {
        restTemplate.exchange(props.getProduct().getUrl() + "/products/" + id,
                HttpMethod.DELETE, new HttpEntity<>(headers()), Void.class);
    }
}
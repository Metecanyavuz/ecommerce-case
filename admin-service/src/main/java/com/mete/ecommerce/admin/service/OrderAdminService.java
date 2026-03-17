package com.mete.ecommerce.admin.service;

import com.mete.ecommerce.admin.config.ServiceProperties;
import com.mete.ecommerce.admin.dto.order.CreateOrderDto;
import com.mete.ecommerce.admin.dto.order.OrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
@Service @RequiredArgsConstructor
public class OrderAdminService {
    private final RestTemplate restTemplate;
    private final ServiceProperties props;
    private final TokenService tokenService;
    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", tokenService.bearerHeader());
        return h;
    }
    public List<OrderDto> getAll() {
        return restTemplate.exchange(props.getOrder().getUrl() + "/orders",
                HttpMethod.GET, new HttpEntity<>(headers()),
                new ParameterizedTypeReference<List<OrderDto>>() {}).getBody();
    }
    public OrderDto getById(Long id) {
        return restTemplate.exchange(props.getOrder().getUrl() + "/orders/" +
                        id,
                HttpMethod.GET, new HttpEntity<>(headers()),
                OrderDto.class).getBody();
    }

    public void createOrder(CreateOrderDto dto) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", tokenService.bearerHeader());
        h.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(props.getOrder().getUrl() + "/orders",
                HttpMethod.POST, new HttpEntity<>(dto, h), OrderDto.class);
    }
    public void updateStatus(Long orderId, String status) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", tokenService.bearerHeader());
        restTemplate.exchange(
                props.getOrder().getUrl() + "/orders/" + orderId + "/status?status=" +
                        status,
                HttpMethod.PATCH, new HttpEntity<>(h), OrderDto.class);
    }
}
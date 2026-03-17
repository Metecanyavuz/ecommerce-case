package com.mete.ecommerce.admin.service;

import com.mete.ecommerce.admin.config.ServiceProperties;
import com.mete.ecommerce.admin.dto.stock.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
@Service @RequiredArgsConstructor
public class StockAdminService {
    private final RestTemplate restTemplate;
    private final ServiceProperties props;
    private final TokenService tokenService;
    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", tokenService.bearerHeader());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
    public StockDto getByProductId(Long productId) {
        try {
            return restTemplate.exchange(props.getStock().getUrl() + "/stocks/"
                            + productId,
                    HttpMethod.GET, new HttpEntity<>(headers()),
                    StockDto.class).getBody();
        } catch (Exception e) { return null; }
    }
    public void increase(Long productId, Integer quantity) {
        StockRequest req = new StockRequest();
        req.setProductId(productId); req.setQuantity(quantity);
        restTemplate.exchange(props.getStock().getUrl() + "/stocks/increase",
                HttpMethod.POST, new HttpEntity<>(req, headers()), StockDto.class);
    }
    public void decrease(Long productId, Integer quantity) {
        StockRequest req = new StockRequest();
        req.setProductId(productId); req.setQuantity(quantity);
        restTemplate.exchange(props.getStock().getUrl() + "/stocks/decrease",
                HttpMethod.POST, new HttpEntity<>(req, headers()), StockDto.class);
    }
}
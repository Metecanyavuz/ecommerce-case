package com.mete.ecommerce.admin.service;

import com.mete.ecommerce.admin.config.ServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class TokenService {
    private final RestTemplate restTemplate;
    private final ServiceProperties serviceProperties;
    @Value("${auth.admin-email}") private String adminEmail;
    @Value("${auth.admin-password}") private String adminPassword;
    private String cachedToken;
    public String getToken() {
        if (cachedToken != null) return cachedToken;
        String url = serviceProperties.getAuth().getUrl() + "/auth/login";
        try {
            ResponseEntity<Map> res = restTemplate.postForEntity(
                    url, Map.of("email", adminEmail, "password", adminPassword),
                    Map.class);
            if (res.getBody() != null) cachedToken = (String)
                    res.getBody().get("token");
        } catch (Exception e) { cachedToken = null; }
        return cachedToken;
    }
    public void invalidate() { cachedToken = null; }
    public String bearerHeader() { return "Bearer " + getToken(); }
}
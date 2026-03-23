package com.mete.ecommerce.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mete.ecommerce.product.config.JwtProperties;
import com.mete.ecommerce.product.config.SecurityConfig;
import com.mete.ecommerce.product.dto.CreateProductRequest;
import com.mete.ecommerce.product.dto.ProductResponse;
import com.mete.ecommerce.product.security.JwtAuthFilter;
import com.mete.ecommerce.product.security.JwtUtil;
import com.mete.ecommerce.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class, JwtProperties.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @Test
    @WithMockUser
    @DisplayName("GET /products - 200 ve liste döner")
    void getAllProducts_shouldReturn200() throws Exception {
        ProductResponse response = new ProductResponse();
        response.setId(1L);
        response.setName("Laptop");
        response.setPrice(new BigDecimal("999.99"));

        when(productService.getAllProducts()).thenReturn(List.of(response));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Laptop"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /products - 201 ve ürün döner")
    void createProduct_shouldReturn201() throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Laptop");
        request.setPrice(new BigDecimal("999.99"));

        ProductResponse response = new ProductResponse();
        response.setId(1L);
        response.setName("Laptop");

        when(productService.createProduct(any())).thenReturn(response);

        mockMvc.perform(post("/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Laptop"));
    }

    @Test
    @DisplayName("POST /products - token olmadan 401/403 döner")
    void createProduct_withoutAuth_shouldReturn403() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}

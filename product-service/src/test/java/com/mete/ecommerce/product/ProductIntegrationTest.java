package com.mete.ecommerce.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mete.ecommerce.product.dto.CreateProductRequest;
import com.mete.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void cleanup() {
        productRepository.deleteAll();
    }

    @Test
    @WithMockUser
    @DisplayName("POST /products → GET /products — eklenen ürün listelenir")
    void createAndListProduct_integrationFlow() throws Exception {
        // 1. Ürün oluştur
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Laptop");
        request.setPrice(new BigDecimal("999.99"));
        request.setCategory("Electronics");

        mockMvc.perform(post("/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Laptop"))
                .andExpect(jsonPath("$.id").exists());

        // 2. Ürün listede var mı?
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Laptop"));

        // 3. DB'de kayıt var mı?
        assertThat(productRepository.findAll()).hasSize(1);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /products/{id} — olmayan ürün 404 döner")
    void getProductById_whenNotFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/products/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /products/{id} — ürün silinince DB'den de kalkar")
    void deleteProduct_shouldRemoveFromDatabase() throws Exception {
        // Önce ürün oluştur
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Silinecek Ürün");
        request.setPrice(new BigDecimal("100.00"));

        String response = mockMvc.perform(post("/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        // Sil
        mockMvc.perform(delete("/products/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        // DB'de yok mu?
        assertThat(productRepository.findById(id)).isEmpty();
    }
}

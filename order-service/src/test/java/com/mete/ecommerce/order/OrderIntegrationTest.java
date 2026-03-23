package com.mete.ecommerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mete.ecommerce.order.client.StockClient;
import com.mete.ecommerce.order.dto.CreateOrderRequest;
import com.mete.ecommerce.order.event.OrderCreatedEvent;
import com.mete.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private StockClient stockClient;

    @MockBean
    private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @AfterEach
    void cleanup() {
        orderRepository.deleteAll();
    }

    @Test
    @WithMockUser
    @DisplayName("POST /orders — order is created, stock is decremented, Kafka event  is sent")
    void createOrder_fullFlow() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setProductId(1L);
        request.setQuantity(5);

        doNothing().when(stockClient).decreaseStock(any());

        mockMvc.perform(post("/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.status").value("CREATED"));

        // does db redcord exist?
        assertThat(orderRepository.findAll()).hasSize(1);

        // stock decreased?
        verify(stockClient, times(1)).decreaseStock(any());

        // Kafka even is sent?
        verify(kafkaTemplate, times(1)).send(anyString(), any(OrderCreatedEvent.class));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /orders — returns 503 when stock service throws error")
    void createOrder_whenStockFails_shouldReturn500() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setProductId(1L);
        request.setQuantity(5);

        doThrow(new RuntimeException("Insufficient stock"))
                .when(stockClient).decreaseStock(any());

        mockMvc.perform(post("/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError());

        // Kafka event shouldn't send
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /orders/{id} — order doesn't exist, returns 404")
    void getOrder_whenNotFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/orders/9999"))
                .andExpect(status().isNotFound());
    }
}

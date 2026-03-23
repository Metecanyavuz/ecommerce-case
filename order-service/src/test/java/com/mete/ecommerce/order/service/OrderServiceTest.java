package com.mete.ecommerce.order.service;

import com.mete.ecommerce.order.client.StockClient;
import com.mete.ecommerce.order.dto.CreateOrderRequest;
import com.mete.ecommerce.order.dto.OrderResponse;
import com.mete.ecommerce.order.dto.StockRequest;
import com.mete.ecommerce.order.entity.Order;
import com.mete.ecommerce.order.entity.OrderStatus;
import com.mete.ecommerce.order.event.OrderCreatedEvent;
import com.mete.ecommerce.order.exception.OrderNotFoundException;
import com.mete.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StockClient stockClient;

    @Mock
    private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = Order.builder()
                .id(1L)
                .customerId(1L)
                .productId(1L)
                .quantity(5)
                .status(OrderStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("createOrder - siparişi kaydeder, stok düşürür, Kafka event gönderir")
    void createOrder_shouldSaveOrderDecreaseStockAndPublishEvent() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setProductId(1L);
        request.setQuantity(5);

        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);

        OrderResponse result = orderService.createOrder(request);

        // Sipariş kaydedildi mi?
        assertThat(result.getCustomerId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);

        // Stok düşürüldü mü?
        verify(stockClient, times(1)).decreaseStock(any(StockRequest.class));

        // Kafka event gönderildi mi?
        verify(kafkaTemplate, times(1)).send(eq("order-created"), any(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("createOrder - stok servisi hata verince exception fırlatır")
    void createOrder_whenStockServiceFails_shouldThrowException() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setProductId(1L);
        request.setQuantity(5);

        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);
        doThrow(new RuntimeException("Yetersiz stok"))
                .when(stockClient).decreaseStock(any(StockRequest.class));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Yetersiz stok");

        // Kafka event gönderilmemeli
        verify(kafkaTemplate, never()).send(anyString(), any(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("getAllOrders - tüm siparişleri döndürür")
    void getAllOrders_shouldReturnAllOrders() {
        when(orderRepository.findAll()).thenReturn(List.of(sampleOrder));

        List<OrderResponse> result = orderService.getAllOrders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getOrderById - mevcut siparişi döndürür")
    void getOrderById_whenExists_shouldReturnOrder() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));

        OrderResponse result = orderService.getOrderById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getOrderById - bulunamazsa OrderNotFoundException fırlatır")
    void getOrderById_whenNotFound_shouldThrowException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(OrderNotFoundException.class);
    }
}

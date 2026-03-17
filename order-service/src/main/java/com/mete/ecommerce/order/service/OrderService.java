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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final StockClient stockClient;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    private static final String ORDER_CREATED_TOPIC = "order-created";

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        // 1. Save the order
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .status(OrderStatus.CREATED)
                .build();
        order = orderRepository.save(order);
        log.info("Order saved: id={}", order.getId());

        // 2. Decrease stock from Stock Service (Feign)
        stockClient.decreaseStock(
                new StockRequest(request.getProductId(), request.getQuantity())
        );
        log.info("Stock decreased for productId={}", request.getProductId());

        // 3. Send Kafka Event
        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity()
        );
        kafkaTemplate.send(ORDER_CREATED_TOPIC, event);
        log.info("Kafka event sent: orderId={}", order.getId());

        return new OrderResponse(order);
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll()
                .stream().map(OrderResponse::new).toList();
    }

    public OrderResponse getOrderById(Long id) {
        return new OrderResponse(
                orderRepository.findById(id)
                        .orElseThrow(() -> new OrderNotFoundException(id))
        );
    }

    public List<OrderResponse> getOrdersByCustomer(Long customerId) {
        return orderRepository.findByCustomerId(customerId)
                .stream().map(OrderResponse::new).toList();
    }

    public OrderResponse updateStatus(Long id, String status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        order.setStatus(OrderStatus.valueOf(status));
        return new OrderResponse(orderRepository.save(order));
    }

}

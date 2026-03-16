package com.mete.ecommerce.order.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Event that will be published to Kafka.
@Getter @NoArgsConstructor @AllArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private Long customerId;
    private Long productId;
    private Integer quantity;
}

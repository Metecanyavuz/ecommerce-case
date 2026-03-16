package com.mete.ecommerce.order.dto;

import com.mete.ecommerce.order.entity.Order;
import com.mete.ecommerce.order.entity.OrderStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class OrderResponse {
    private final Long id;
    private final Long customerId;
    private final long productId;
    private final Integer quantity;
    private final OrderStatus status;
    private final LocalDateTime createdAt;

    public OrderResponse(Order order) {
        this.id = order.getId();
        this.customerId = order.getCustomerId();
        this.productId = order.getProductId();
        this.quantity = order.getQuantity();
        this.status = order.getStatus();
        this.createdAt = order.getCreatedAt();
    }
}

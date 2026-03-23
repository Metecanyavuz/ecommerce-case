package com.mete.ecommerce.order.dto;

import com.mete.ecommerce.order.entity.Order;
import com.mete.ecommerce.order.entity.OrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class OrderResponse {
    private  Long id;
    private  Long customerId;
    private  long productId;
    private  Integer quantity;
    private  OrderStatus status;
    private  LocalDateTime createdAt;

    public OrderResponse(Order order) {
        this.id = order.getId();
        this.customerId = order.getCustomerId();
        this.productId = order.getProductId();
        this.quantity = order.getQuantity();
        this.status = order.getStatus();
        this.createdAt = order.getCreatedAt();
    }

    public OrderResponse() {
    }

}

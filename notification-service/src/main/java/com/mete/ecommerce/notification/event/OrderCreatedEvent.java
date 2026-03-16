package com.mete.ecommerce.notification.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor@AllArgsConstructor
@ToString
public class OrderCreatedEvent {

    private Long orderId;
    private Long customerId;
    private Long productId;
    private Integer quantity;

}

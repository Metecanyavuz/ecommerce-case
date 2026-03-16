package com.mete.ecommerce.notification.consumer;

import com.mete.ecommerce.notification.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationConsumer {

    @KafkaListener(
            topics = "order-created",
            groupId = "notification-group"
    )

    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("==========================================");
        log.info("[NOTIFICATION] New order arrived!");
        log.info("[NOTIFICATION] Order ID  : {}", event.getOrderId());
        log.info("[NOTIFICATION] Customer  : {}", event.getCustomerId());
        log.info("[NOTIFICATION] Product   : {}", event.getProductId());
        log.info("[NOTIFICATION] Quantity  : {}", event.getQuantity());
        log.info("[EMAIL SIM] Verification mail sent to customer #{} .", event.getCustomerId());
        log.info("==========================================");
    }
}



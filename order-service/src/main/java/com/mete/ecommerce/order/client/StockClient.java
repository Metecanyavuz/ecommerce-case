package com.mete.ecommerce.order.client;

import com.mete.ecommerce.order.config.FeignClientConfig;
import com.mete.ecommerce.order.dto.StockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "stock-service",
        url = "${services.stock.url}",
        configuration = FeignClientConfig.class
)
public interface StockClient {

    @PostMapping("/stocks/decrease")
    void decreaseStock(@RequestBody StockRequest request);
}

    //ℹ️ Why does Feign only call decrease?:
    //Order Service handles stock control itself at the service layer (it checks the DB before Stock Service).
    //Decrease is called after the order is saved — maintaining this order is important.




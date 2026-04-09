package com.mete.ecommerce.admin.config;

import lombok.Getter; import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "services")
public class ServiceProperties {
    private Service auth = new Service();
    private Service customer = new Service();
    private Service product = new Service();
    private Service stock = new Service();
    private Service order = new Service();
    @Getter @Setter
    public static class Service {
        private String url;
    }
}
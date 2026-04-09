package com.mete.ecommerce.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Admin Service API")
                        .version("1.0.0")
                        .description("Thymeleaf-based admin panel endpoints for managing customers, products, orders, stock, and users.")
                        .contact(new Contact()
                                .name("Ecommerce Platform Team")
                                .email("dev@ecommerce.local")))
                .servers(List.of(
                        new Server().url("http://localhost:8090").description("Local Development")));
    }
}

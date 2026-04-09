package com.mete.ecommerce.customer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api-docs/**").allowedOrigins("*").allowedMethods("GET");
        registry.addMapping("/v3/api-docs/**").allowedOrigins("*").allowedMethods("GET");
        registry.addMapping("/scalar").allowedOrigins("*").allowedMethods("GET");
    }

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Customer Service API")
                        .version("1.0.0")
                        .description("Manages customer profiles including creation, retrieval, updates, and deletion.")
                        .contact(new Contact()
                                .name("Ecommerce Platform Team")
                                .email("dev@ecommerce.local")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("Local Development")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT token (without 'Bearer ' prefix)")));
    }
}

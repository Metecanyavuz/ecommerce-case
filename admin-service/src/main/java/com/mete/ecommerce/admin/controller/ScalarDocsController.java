package com.mete.ecommerce.admin.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ScalarDocsController {

    @Value("${services.auth.url}")
    private String authUrl;

    @Value("${services.customer.url}")
    private String customerUrl;

    @Value("${services.product.url}")
    private String productUrl;

    @Value("${services.stock.url}")
    private String stockUrl;

    @Value("${services.order.url}")
    private String orderUrl;

    @GetMapping("/scalar")
    @ResponseBody
    public ResponseEntity<String> scalarUi() {
        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <title>E-Commerce Platform — API Reference</title>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <style>body { margin: 0; padding: 0; }</style>
                </head>
                <body>
                  <div id="app"></div>
                  <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
                  <script>
                    Scalar.createApiReference('#app', {
                      sources: [
                        { url: '%s/api-docs', title: 'Auth Service',     slug: 'auth' },
                        { url: '%s/api-docs', title: 'Customer Service', slug: 'customer' },
                        { url: '%s/api-docs', title: 'Product Service',  slug: 'product' },
                        { url: '%s/api-docs', title: 'Stock Service',    slug: 'stock' },
                        { url: '%s/api-docs', title: 'Order Service',    slug: 'order' },
                        { url: '/api-docs',   title: 'Admin Service',    slug: 'admin' },
                      ],
                      theme: 'default',
                      layout: 'modern',
                      darkMode: true,
                      showSidebar: true,
                      hideModels: false,
                      hideTestRequestButton: false,
                      hideSearch: false,
                      withDefaultFonts: true,
                      metaData: {
                        title: 'E-Commerce Platform API Reference',
                        description: 'Unified API documentation for all e-commerce microservices.',
                        ogTitle: 'E-Commerce Platform API Reference',
                      },
                      authentication: {
                        preferredSecurityScheme: 'bearerAuth',
                      },
                    })
                  </script>
                </body>
                </html>
                """.formatted(authUrl, customerUrl, productUrl, stockUrl, orderUrl);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}

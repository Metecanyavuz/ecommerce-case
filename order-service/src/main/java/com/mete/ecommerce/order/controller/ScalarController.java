package com.mete.ecommerce.order.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Hidden
@Controller
public class ScalarController {

    @GetMapping("/scalar")
    @ResponseBody
    public ResponseEntity<String> scalarUi() {
        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <title>Order Service — API Reference</title>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <style>body { margin: 0; padding: 0; }</style>
                </head>
                <body>
                  <div id="app"></div>
                  <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
                  <script>
                    Scalar.createApiReference('#app', {
                      url: '/api-docs',
                      theme: 'default',
                      layout: 'modern',
                      darkMode: true,
                      showSidebar: true,
                      hideModels: false,
                      hideTestRequestButton: false,
                      hideSearch: false,
                      withDefaultFonts: true,
                      metaData: {
                        title: 'Order Service API Reference',
                        description: 'Manages customer orders: placement, retrieval by ID or customer, and status updates.',
                        ogTitle: 'Order Service API Reference',
                      },
                      authentication: {
                        preferredSecurityScheme: 'bearerAuth',
                      },
                    })
                  </script>
                </body>
                </html>
                """;
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}

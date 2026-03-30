package com.mete.ecommerce.product.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ScalarController {

    @GetMapping("/scalar")
    @ResponseBody
    public ResponseEntity<String> scalarUi() {
        String html = """
                <!doctype html>
                <html>
                <head>
                  <title>Product Service API Reference</title>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                </head>
                <body>
                  <div id="app"></div>
                  <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
                  <script>
                    Scalar.createApiReference('#app', {
                      url: '/api-docs',
                      theme: 'moon',
                      darkMode: true,
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

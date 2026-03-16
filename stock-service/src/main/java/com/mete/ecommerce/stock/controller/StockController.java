package com.mete.ecommerce.stock.controller;

import com.mete.ecommerce.stock.dto.StockRequest;
import com.mete.ecommerce.stock.dto.StockResponse;
import com.mete.ecommerce.stock.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> getByProductId(@PathVariable Long productId) {
        return ResponseEntity.ok(stockService.getByProductId(productId));
    }

    @PostMapping("/increase")
    public ResponseEntity<StockResponse> increase(@Valid@RequestBody StockRequest request){
        return ResponseEntity.ok(stockService.increase(request));
    }

    @PostMapping("/decrease")
    public ResponseEntity<StockResponse> decrease (@Valid@RequestBody StockRequest request){
        return ResponseEntity.ok(stockService.decrease(request));
    }

}

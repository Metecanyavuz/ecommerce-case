package com.mete.ecommerce.product.grpc;

import com.mete.ecommerce.product.dto.CreateProductRequest;
import com.mete.ecommerce.product.dto.ProductResponse;
import com.mete.ecommerce.product.dto.UpdateProductRequest;
import com.mete.ecommerce.product.service.ProductService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.List;

@GrpcService  // Spring bean + gRPC service kaydı
@RequiredArgsConstructor
public class GrpcProductService extends ProductGrpcServiceGrpc.ProductGrpcServiceImplBase {

    private final ProductService productService;  // Mevcut service'i yeniden kullan

    @Override
    public void getProduct(ProductRequest request,
                           StreamObserver<ProductGrpcResponse> responseObserver) {
        ProductResponse product = productService.getProductById(request.getId());
        responseObserver.onNext(toGrpcResponse(product));
        responseObserver.onCompleted();
    }

    @Override
    public void listProducts(Empty request,
                             StreamObserver<ProductListGrpcResponse> responseObserver) {
        List<ProductGrpcResponse> grpcList = productService.getAllProducts()
                .stream()
                .map(this::toGrpcResponse)
                .toList();

        responseObserver.onNext(
                ProductListGrpcResponse.newBuilder().addAllProducts(grpcList).build()
        );
        responseObserver.onCompleted();
    }

    @Override
    public void createProduct(CreateProductGrpcRequest request,
                              StreamObserver<ProductGrpcResponse> responseObserver) {
        CreateProductRequest dto = new CreateProductRequest();
        dto.setName(request.getName());
        dto.setDescription(request.getDescription());
        dto.setPrice(new BigDecimal(request.getPrice()));
        dto.setCategory(request.getCategory());

        responseObserver.onNext(toGrpcResponse(productService.createProduct(dto)));
        responseObserver.onCompleted();
    }

    @Override
    public void updateProduct(UpdateProductGrpcRequest request,
                              StreamObserver<ProductGrpcResponse> responseObserver) {
        UpdateProductRequest dto = new UpdateProductRequest();
        if (!request.getName().isEmpty())        dto.setName(request.getName());
        if (!request.getDescription().isEmpty()) dto.setDescription(request.getDescription());
        if (!request.getPrice().isEmpty())       dto.setPrice(new BigDecimal(request.getPrice()));
        if (!request.getCategory().isEmpty())    dto.setCategory(request.getCategory());

        responseObserver.onNext(toGrpcResponse(productService.updateProduct(request.getId(), dto)));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteProduct(DeleteProductRequest request,
                              StreamObserver<DeleteProductResponse> responseObserver) {
        productService.deleteProduct(request.getId());
        responseObserver.onNext(DeleteProductResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    private ProductGrpcResponse toGrpcResponse(ProductResponse p) {
        return ProductGrpcResponse.newBuilder()
                .setId(p.getId())
                .setName(p.getName() != null ? p.getName() : "")
                .setDescription(p.getDescription() != null ? p.getDescription() : "")
                .setPrice(p.getPrice() != null ? p.getPrice().toPlainString() : "")
                .setCategory(p.getCategory() != null ? p.getCategory() : "")
                .setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "")
                .build();
    }
}

//Neden aynı ProductService bean'i kullanıyoruz?
// Benchmark'ın ölçtüğü şey transport katmanı (REST vs gRPC). İş mantığı ve DB sorgusu aynı olmalı — aksi hâlde karşılaştırma anlamsızlaşır.
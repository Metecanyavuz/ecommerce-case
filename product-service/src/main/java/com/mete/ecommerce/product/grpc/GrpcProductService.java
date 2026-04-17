package com.mete.ecommerce.product.grpc;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mete.ecommerce.product.cache.ProductCacheService;
import com.mete.ecommerce.product.dto.CreateProductRequest;
import com.mete.ecommerce.product.dto.ProductResponse;
import com.mete.ecommerce.product.dto.UpdateProductRequest;
import com.mete.ecommerce.product.service.ProductService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.List;

@GrpcService  // Spring bean + gRPC service kaydı
@RequiredArgsConstructor
public class GrpcProductService extends ProductGrpcServiceGrpc.ProductGrpcServiceImplBase {

    private final ProductService      productService;   // Mevcut service'i yeniden kullan
    private final ProductCacheService productCacheService;

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

    // ── Server Streaming: gRPC'nin büyük liste için doğal modeli ─────────────
    // Unary ListProducts'tan farkı: tüm listeyi tek mesajda buffer'lamaz.
    // Her ürün ayrı frame olarak gönderilir → time-to-first-product düşer,
    // TCP flow-control sorunsuz çalışır, HOL riski minimuma düşer.
    // Karşılaştırma: k6 S2 (unary) vs ghz G_STREAM (streaming)
    @Override
    public void streamProducts(Empty request,
                               StreamObserver<ProductGrpcResponse> responseObserver) {
        productService.getAllProducts()
                .forEach(p -> responseObserver.onNext(toGrpcResponse(p)));
        responseObserver.onCompleted();
    }

    // ── Cached gRPC: Redis Protobuf bytes → single parseFrom (S11) ───────────
    // REST cached path: proto bytes → List<ProductResponse> → Jackson → JSON  (2× serializasyon)
    // gRPC cached path: proto bytes → parseFrom → onNext()                    (1× serializasyon)
    // Bu farkı benchmark'ta ölçmek S5/S6 REST cache karşısında gRPC'nin gerçek avantajını gösterir.
    @Override
    public void listProductsCachedGrpc(Empty request,
                                       StreamObserver<ProductListGrpcResponse> responseObserver) {
        try {
            byte[] bytes = productCacheService.getAllProductsProtobufBytes();
            responseObserver.onNext(ProductListGrpcResponse.parseFrom(bytes));
            responseObserver.onCompleted();
        } catch (InvalidProtocolBufferException e) {
            responseObserver.onError(
                Status.INTERNAL.withDescription("Cache parse error: " + e.getMessage()).asException()
            );
        }
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
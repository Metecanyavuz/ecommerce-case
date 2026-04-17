package com.mete.ecommerce.product.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mete.ecommerce.product.dto.ProductResponse;
import com.mete.ecommerce.product.grpc.ProductGrpcResponse;
import com.mete.ecommerce.product.grpc.ProductListGrpcResponse;
import com.mete.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cache-aside pattern — iki ayrı serializasyon stratejisiyle:
 *
 *  JSON path  : ObjectMapper → UTF-8 bytes → Redis
 *               Redis → UTF-8 bytes → ObjectMapper
 *
 *  Proto path : ProductListGrpcResponse.toByteArray() → Redis
 *               Redis → ProductListGrpcResponse.parseFrom() → ProductResponse list
 *
 * Her iki path da aynı endpoint flow'unu izler:
 *   1. Redis'te var mı? → var ise deserialize edip dön
 *   2. Yok ise DB'den çek → serialize edip Redis'e yaz → dön
 *   3. Redis'e erişilemiyorsa DB'ye fall-back et (hata durumu)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheService {

    static final String JSON_KEY  = "products:list:json";
    static final String PROTO_KEY = "products:list:proto";
    // Benchmark süresi ~1400s — 60s TTL setup() warm-up'ı geçersiz kılıyordu.
    // 3600s: bir oturum boyunca cache stabil kalır; gerçek invalidation testi için
    // TTL'i kısaltmak yerine Redis'i manuel flush edin (redis-cli FLUSHDB).
    static final long   TTL_SEC   = 3600;

    private final RedisTemplate<String, byte[]> byteRedisTemplate;
    private final ProductRepository             productRepository;
    private final ObjectMapper                  objectMapper;

    // ── JSON cache ────────────────────────────────────────────────────────────

    public List<ProductResponse> getAllProductsJson() {
        try {
            byte[] cached = byteRedisTemplate.opsForValue().get(JSON_KEY);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            }
            List<ProductResponse> products = fetchFromDb();
            byteRedisTemplate.opsForValue()
                    .set(JSON_KEY, objectMapper.writeValueAsBytes(products), TTL_SEC, TimeUnit.SECONDS);
            return products;
        } catch (Exception e) {
            log.warn("Redis JSON cache error — falling back to DB: {}", e.getMessage());
            return fetchFromDb();
        }
    }

    // ── Protobuf cache ────────────────────────────────────────────────────────

    public List<ProductResponse> getAllProductsProtobuf() {
        try {
            byte[] cached = byteRedisTemplate.opsForValue().get(PROTO_KEY);
            if (cached != null) {
                return fromProtoBytes(cached);
            }
            List<ProductResponse> products = fetchFromDb();
            byteRedisTemplate.opsForValue()
                    .set(PROTO_KEY, toProtoBytes(products), TTL_SEC, TimeUnit.SECONDS);
            return products;
        } catch (Exception e) {
            log.warn("Redis Protobuf cache error — falling back to DB: {}", e.getMessage());
            return fetchFromDb();
        }
    }

    // ── gRPC cache — raw bytes ────────────────────────────────────────────────
    // GrpcProductService'in doğrudan Protobuf bytes okuyup parseFrom() yapması için.
    // REST path: proto bytes → List<ProductResponse> → Jackson → JSON  (2× dönüşüm)
    // gRPC path: proto bytes → parseFrom → onNext()                    (1× dönüşüm)
    // Bu farkı ölçmek S5/S6 REST vs S11 gRPC cache karşılaştırmasının özüdür.
    public byte[] getAllProductsProtobufBytes() {
        try {
            byte[] cached = byteRedisTemplate.opsForValue().get(PROTO_KEY);
            if (cached != null) return cached;
            List<ProductResponse> products = fetchFromDb();
            byte[] bytes = toProtoBytes(products);
            byteRedisTemplate.opsForValue().set(PROTO_KEY, bytes, TTL_SEC, TimeUnit.SECONDS);
            return bytes;
        } catch (Exception e) {
            log.warn("Redis Protobuf bytes cache error — building fresh: {}", e.getMessage());
            return toProtoBytes(fetchFromDb());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<ProductResponse> fetchFromDb() {
        return productRepository.findAll().stream()
                .map(ProductResponse::new)
                .toList();
    }

    private byte[] toProtoBytes(List<ProductResponse> products) {
        List<ProductGrpcResponse> grpcList = products.stream()
                .map(this::toGrpcProto)
                .toList();
        return ProductListGrpcResponse.newBuilder()
                .addAllProducts(grpcList)
                .build()
                .toByteArray();
    }

    private List<ProductResponse> fromProtoBytes(byte[] bytes) throws InvalidProtocolBufferException {
        return ProductListGrpcResponse.parseFrom(bytes)
                .getProductsList()
                .stream()
                .map(this::fromGrpcProto)
                .toList();
    }

    private ProductGrpcResponse toGrpcProto(ProductResponse p) {
        return ProductGrpcResponse.newBuilder()
                .setId(p.getId())
                .setName(p.getName() != null ? p.getName() : "")
                .setDescription(p.getDescription() != null ? p.getDescription() : "")
                .setPrice(p.getPrice() != null ? p.getPrice().toPlainString() : "")
                .setCategory(p.getCategory() != null ? p.getCategory() : "")
                .setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "")
                .build();
    }

    private ProductResponse fromGrpcProto(ProductGrpcResponse g) {
        ProductResponse r = new ProductResponse();
        r.setId(g.getId());
        r.setName(g.getName());
        r.setDescription(g.getDescription());
        r.setPrice(g.getPrice().isEmpty() ? null : new BigDecimal(g.getPrice()));
        r.setCategory(g.getCategory());
        r.setCreatedAt(g.getCreatedAt().isEmpty() ? null : LocalDateTime.parse(g.getCreatedAt()));
        return r;
    }
}

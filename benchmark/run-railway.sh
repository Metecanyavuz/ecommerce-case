#!/bin/sh
# Railway benchmark runner — S1-S6, ~16 dakika, otomatik karşılaştırma raporu

set -e

REST_URL=${REST_URL:-"http://product-service.railway.internal:8080"}
GRPC_URL=${GRPC_URL:-"product-service.railway.internal:9090"}
MAX_SPIKE_VU=${MAX_SPIKE_VU:-500}

echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║      gRPC vs REST vs Redis Cache Multi-Scenario Benchmark (Railway)  ║"
echo "╠══════════════════════════════════════════════════════════════════════╣"
echo "║  REST  → $REST_URL"
echo "║  gRPC  → $GRPC_URL"
echo "╠══════════════════════════════════════════════════════════════════════╣"
echo "║  S1: Küçük Payload         (GetProduct,    1 ürün,   50 VU)         ║"
echo "║  S2: Büyük Payload         (ListProducts, ~20MB,     50 VU)         ║"
echo "║  S3: Yüksek Eşzamanlılık   (ListProducts, ~20MB,    200 VU)         ║"
echo "║  S4: Flash Sale Spike      (GetProduct,   0→${MAX_SPIKE_VU} VU, 10s'de)     ║"
echo "║  S5: Redis JSON Cache      (ListProducts, warm,      50 VU)         ║"
echo "║  S6: Redis Protobuf Cache  (ListProducts, warm,      50 VU)         ║"
echo "╠══════════════════════════════════════════════════════════════════════╣"
echo "║  Tahmini toplam süre: ~16 dakika                                     ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""

REST_URL=$REST_URL GRPC_URL=$GRPC_URL MAX_SPIKE_VU=$MAX_SPIKE_VU k6 run /scripts/benchmark.js

echo ""
echo "══════════════════════════════════════════════════════════════════════"
echo "  Benchmark tamamlandı."
echo "  (Railway Logs sekmesinden yukarı kaydırarak raporu görebilirsin)"
echo "══════════════════════════════════════════════════════════════════════"

sleep infinity

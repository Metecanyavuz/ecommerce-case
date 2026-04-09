#!/bin/sh
# Railway benchmark runner — 6 senaryo, ~10 dakika, otomatik karşılaştırma raporu

set -e

REST_URL=${REST_URL:-"http://product-service.railway.internal:8080"}
GRPC_URL=${GRPC_URL:-"product-service.railway.internal:9090"}

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║        gRPC vs REST Multi-Scenario Benchmark (Railway)       ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  REST  → $REST_URL"
echo "║  gRPC  → $GRPC_URL"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  S1: Küçük Payload   — GetProduct    (1 ürün,   50 VU)       ║"
echo "║  S2: Büyük Payload   — ListProducts  (1000 ürün, 50 VU)      ║"
echo "║  S3: Yüksek Eşzaman  — ListProducts  (1000 ürün, 200 VU)     ║"
echo "║  Toplam süre: ~10 dakika                                      ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

REST_URL=$REST_URL GRPC_URL=$GRPC_URL k6 run /scripts/benchmark.js

echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  Benchmark tamamlandı. Container log okuma için bekliyor."
echo "  (Railway Logs sekmesinden yukarı kaydırarak raporu görebilirsin)"
echo "══════════════════════════════════════════════════════════════"

sleep infinity

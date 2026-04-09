#!/bin/sh
# Railway'de çalışır: her iki benchmark'ı sırayla çalıştırır,
# ardından container'ı canlı tutar (Railway log'larını okuyabilmek için).

set -e

REST_URL=${REST_URL:-"http://product-service.railway.internal:8083"}
GRPC_URL=${GRPC_URL:-"product-service.railway.internal:9090"}

echo "================================================="
echo "  gRPC vs REST Performance Benchmark (Railway)"
echo "  REST  → $REST_URL"
echo "  gRPC  → $GRPC_URL"
echo "================================================="

echo ""
echo "[ 1/2 ] Running REST benchmark..."
echo "-------------------------------------------------"
REST_URL=$REST_URL k6 run /scripts/rest-benchmark.js

echo ""
echo "[ 2/2 ] Running gRPC benchmark..."
echo "-------------------------------------------------"
GRPC_URL=$GRPC_URL k6 run /scripts/grpc-benchmark.js

echo ""
echo "================================================="
echo "  Done! Keeping container alive for log reading."
echo "  (You can now stop the Railway service.)"
echo "================================================="

sleep infinity

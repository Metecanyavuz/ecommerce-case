#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# gRPC vs REST Benchmark Runner
#
# Kullanım:
#   REST_URL=https://your-host:8083 GRPC_URL=your-host:9090 bash run-comparison.sh
#
# Yoksa localhost fallback kullanılır.
# ─────────────────────────────────────────────────────────────────
set -e

REST_URL=${REST_URL:-"http://localhost:8083"}
GRPC_URL=${GRPC_URL:-"localhost:9090"}

# results/ klasörü yoksa oluştur
mkdir -p results

echo ""
echo "================================================="
echo "  gRPC vs REST Performance Benchmark"
echo "  REST  → $REST_URL"
echo "  gRPC  → $GRPC_URL"
echo "================================================="

echo ""
echo "[ 1/2 ] Running REST benchmark..."
echo "-------------------------------------------------"
REST_URL=$REST_URL k6 run rest-benchmark.js

echo ""
echo "[ 2/2 ] Running gRPC benchmark..."
echo "-------------------------------------------------"
GRPC_URL=$GRPC_URL k6 run grpc-benchmark.js

echo ""
echo "================================================="
echo "  Done! Results saved to ./results/"
echo "    rest-summary.json"
echo "    grpc-summary.json"
echo "================================================="

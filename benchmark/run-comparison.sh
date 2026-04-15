#!/bin/bash
# ══════════════════════════════════════════════════════════════════════
#  gRPC vs REST vs Redis Cache Benchmark Runner
#
#  Tek veya iki makine için çalışır.
#
#  Kullanım:
#    # İki makine (önerilen):
#    REST_URL=http://<servis-ip>:8083 GRPC_URL=<servis-ip>:9090 bash run-comparison.sh
#
#    # Tek makine (local docker-compose):
#    bash run-comparison.sh
#
#  Tam AWS kurulumu için: run-aws.sh
# ══════════════════════════════════════════════════════════════════════
set -e

REST_URL=${REST_URL:-"http://localhost:8083"}
GRPC_URL=${GRPC_URL:-"localhost:9090"}
MAX_SPIKE_VU=${MAX_SPIKE_VU:-100}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║          gRPC vs REST vs Redis Cache Benchmark                       ║"
printf "║  REST  → %-60s║\n" "$REST_URL"
printf "║  gRPC  → %-60s║\n" "$GRPC_URL"
echo "╠══════════════════════════════════════════════════════════════════════╣"
echo "║  S1-S4: REST vs gRPC (4 senaryo)                                     ║"
echo "║  S5:    Redis JSON cache — warm cache hit (50 VU)                    ║"
echo "║  S6:    Redis Protobuf cache — warm cache hit (50 VU)                ║"
echo "╠══════════════════════════════════════════════════════════════════════╣"
echo "║  Not: S5/S6 setup() ile önceden ısıtılır — tüm ölçümler cache hit   ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""

REST_URL="$REST_URL" \
GRPC_URL="$GRPC_URL" \
MAX_SPIKE_VU="$MAX_SPIKE_VU" \
k6 run benchmark.js

echo ""
echo "══════════════════════════════════════════════════════════════════════"
echo "  Tamamlandı."
echo "══════════════════════════════════════════════════════════════════════"

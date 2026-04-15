#!/bin/bash
# ══════════════════════════════════════════════════════════════════════
#  AWS İki-Makine Benchmark Runner
#
#  Topoloji:
#    EC2-A (c5.xlarge/2xlarge) — tüm servisler + infra (docker-compose)
#    EC2-B (t3.medium)         — bu script + k6 (yalnızca bu makine)
#
#  Kullanım (EC2-B üzerinde):
#    REST_URL=http://<EC2-A-PRIVATE-IP>:8083 \
#    GRPC_URL=<EC2-A-PRIVATE-IP>:9090        \
#    bash run-aws.sh
#
#  Opsiyonel:
#    MAX_SPIKE_VU=200   # default: 500
# ══════════════════════════════════════════════════════════════════════
set -e

REST_URL=${REST_URL:-"http://localhost:8083"}
GRPC_URL=${GRPC_URL:-"localhost:9090"}
MAX_SPIKE_VU=${MAX_SPIKE_VU:-500}

# k6 kurulu mu?
if ! command -v k6 &> /dev/null; then
  echo "[ERROR] k6 bulunamadı. Kurulum:"
  echo "  sudo gpg -k"
  echo "  sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69"
  echo "  echo 'deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main' | sudo tee /etc/apt/sources.list.d/k6.list"
  echo "  sudo apt-get update && sudo apt-get install k6"
  exit 1
fi

# Servis erişilebilirlik kontrolü
echo ""
echo "[ Ön Kontrol ] Servislere erişim test ediliyor..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${REST_URL}/products/1" || echo "000")
if [ "$HTTP_CODE" != "200" ]; then
  echo "[WARN] REST endpoint yanıt vermedi (HTTP $HTTP_CODE). EC2-A'nın çalıştığını ve Security Group kurallarını kontrol et."
  echo "       Port 8083 ve 9090 için EC2-B → EC2-A inbound rule gerekli."
  read -p "  Yine de devam et? (y/N): " confirm
  [[ "$confirm" == "y" || "$confirm" == "Y" ]] || exit 1
else
  echo "  REST ✓  (HTTP $HTTP_CODE)"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║          AWS Benchmark — REST vs gRPC vs Redis Cache                 ║"
echo "╠══════════════════════════════════════════════════════════════════════╣"
printf "║  REST  → %-60s║\n" "$REST_URL"
printf "║  gRPC  → %-60s║\n" "$GRPC_URL"
echo "╠══════════════════════════════════════════════════════════════════════╣"
echo "║  S1: Küçük Payload         (GetProduct,    1 ürün,   50 VU,  ~115s) ║"
echo "║  S2: Büyük Payload         (ListProducts, ~20MB,     50 VU,  ~115s) ║"
echo "║  S3: Yüksek Eşzamanlılık   (ListProducts, ~20MB,    200 VU,   ~75s) ║"
echo "║  S4: Flash Sale Spike      (GetProduct,   0→${MAX_SPIKE_VU} VU,  10s,   ~55s) ║"
echo "║  S5: Redis JSON Cache      (ListProducts, warm,      50 VU,  ~110s) ║"
echo "║  S6: Redis Protobuf Cache  (ListProducts, warm,      50 VU,  ~110s) ║"
echo "╠══════════════════════════════════════════════════════════════════════╣"
echo "║  Tahmini toplam süre: ~16 dakika                                     ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""

cd "$SCRIPT_DIR"

REST_URL="$REST_URL" \
GRPC_URL="$GRPC_URL" \
MAX_SPIKE_VU="$MAX_SPIKE_VU" \
k6 run benchmark.js

echo ""
echo "══════════════════════════════════════════════════════════════════════"
echo "  Benchmark tamamlandı."
echo "══════════════════════════════════════════════════════════════════════"

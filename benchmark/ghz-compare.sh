#!/bin/bash
# ══════════════════════════════════════════════════════════════════════
#  ghz — True gRPC Multiplexing + Head-of-Line Blocking Analizi
#
#  k6'nın gösteremediği şeyleri ölçer:
#    k6 modeli   : Her VU = ayrı bağlantı, 1 stream/bağlantı (sequential)
#    Gerçek üretim: 1 bağlantı, N eş zamanlı stream (HTTP/2 multiplexing)
#
#  Senaryolar (çalışma sırası):
#    G1: GetProduct  · 50 concurrent  — multiplexing karşılaştırması  (k6 S1/S7 eşdeğeri)
#    G2: ListProducts · 10 concurrent — büyük payload HoL tespiti      (k6 S2 eşdeğeri)
#    G5: Head-of-Line Blocking testi  — 20MB akışı altında GetProduct latency
#    G4: GetProduct pool simülasyonu                                   (k6 S9 gRPC eşdeğeri)
#    G3: ListProducts · 30 concurrent — stres testi, EN SONDA          (k6 S3 eşdeğeri)
#        ↑ G3 kasıtlı olarak son sıradadır: sunucuyu hasar bırakabilir,
#          önceki testleri zehirlememesi için buraya alındı.
#
#  Kullanım (EC2-B üzerinde, k6 benchmark'ının ardından):
#    GRPC_URL=<EC2-A-PRIVATE-IP>:9090 bash ghz-compare.sh
#    MS_POOL_VU=10 GRPC_URL=... bash ghz-compare.sh   # pool boyutunu k6 S9 ile eşitle
#
#  ghz kurulumu (henüz yoksa):
#    wget https://github.com/bojand/ghz/releases/latest/download/ghz_Linux_x86_64.tar.gz
#    tar -xzf ghz_Linux_x86_64.tar.gz && sudo mv ghz /usr/local/bin/
#    rm ghz_Linux_x86_64.tar.gz
# ══════════════════════════════════════════════════════════════════════
set -e

GRPC_URL=${GRPC_URL:-"localhost:9090"}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROTO="$SCRIPT_DIR/proto/product.proto"
OUT_DIR="$SCRIPT_DIR/ghz-results"
DURATION=60      # saniye — her varyant için
POOL_VU=${MS_POOL_VU:-10}   # k6 S9 REST pool boyutuyla eşleştirilir

mkdir -p "$OUT_DIR"

# ── ghz kurulu mu? ────────────────────────────────────────────────────────────
if ! command -v ghz &>/dev/null; then
  echo "[ERROR] ghz bulunamadı."
  echo ""
  echo "  Kurulum:"
  echo "    wget https://github.com/bojand/ghz/releases/latest/download/ghz_Linux_x86_64.tar.gz"
  echo "    tar -xzf ghz_Linux_x86_64.tar.gz && sudo mv ghz /usr/local/bin/"
  echo "    rm ghz_Linux_x86_64.tar.gz"
  exit 1
fi

# ── jq kurulu mu? ─────────────────────────────────────────────────────────────
if ! command -v jq &>/dev/null; then
  echo "[ERROR] jq bulunamadı: sudo apt-get install -y jq"
  exit 1
fi

# ── Proto erişim kontrolü ─────────────────────────────────────────────────────
if [ ! -f "$PROTO" ]; then
  echo "[ERROR] Proto dosyası bulunamadı: $PROTO"
  exit 1
fi

# ── Sunucu erişim kontrolü ───────────────────────────────────────────────────
HOST=$(echo "$GRPC_URL" | cut -d: -f1)
PORT=$(echo "$GRPC_URL" | cut -d: -f2)
echo ""
echo "[ Ön Kontrol ] gRPC sunucusu test ediliyor: $GRPC_URL"
if ! nc -z -w3 "$HOST" "$PORT" 2>/dev/null; then
  echo "[WARN] $GRPC_URL erişilemiyor."
  read -p "  Yine de devam et? (y/N): " confirm
  [[ "$confirm" == "y" || "$confirm" == "Y" ]] || exit 1
else
  echo "  gRPC ✓  ($GRPC_URL)"
fi

# ── Test fonksiyonu ───────────────────────────────────────────────────────────
# run_ghz <etiket> <method> <data> <connections> <concurrency>
run_ghz() {
  local label="$1"
  local method="$2"
  local data="$3"
  local conns="$4"
  local conc="$5"
  local outfile="$OUT_DIR/${label}.json"

  printf "    %-48s" "$label (conns=$conns, concurrent=$conc)"

  ghz --insecure \
      --proto "$PROTO" \
      --call "$method" \
      --data "$data" \
      --connections "$conns" \
      --concurrency "$conc" \
      --duration "${DURATION}s" \
      --format json \
      --output "$outfile" \
      "$GRPC_URL" 2>/dev/null

  if [ $? -ne 0 ] || [ ! -f "$outfile" ]; then
    echo "  → [HATA]"
    return
  fi

  local avg_ns=$(jq -r '.average // 0' "$outfile")
  local p95_ns=$(jq -r '.latencyDistribution[] | select(.percentage==95) | .latency' "$outfile" 2>/dev/null | head -1)
  local p99_ns=$(jq -r '.latencyDistribution[] | select(.percentage==99) | .latency' "$outfile" 2>/dev/null | head -1)
  local rps=$(jq -r '.rps // 0' "$outfile")
  local total=$(jq -r '.count // 0' "$outfile")
  local errs=$(jq -r '[.errorDistribution // {} | to_entries[] | .value] | add // 0' "$outfile")

  local avg_ms=$(awk "BEGIN {printf \"%.1f\", $avg_ns/1000000}")
  local p95_ms=$(awk "BEGIN {printf \"%.1f\", ${p95_ns:-0}/1000000}")
  local p99_ms=$(awk "BEGIN {printf \"%.1f\", ${p99_ns:-0}/1000000}")
  local rps_fmt=$(awk "BEGIN {printf \"%.0f\", $rps}")
  local err_pct=$(awk "BEGIN {printf \"%.2f\", ($total > 0) ? ($errs/$total*100) : 0}")

  echo "  avg=${avg_ms}ms  p95=${p95_ms}ms  p99=${p99_ms}ms  rps=${rps_fmt}/s  hata=%${err_pct}"
}

# ── Başlık ────────────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║  ghz — True gRPC Multiplexing Karşılaştırması                       ║"
printf "║  gRPC → %-62s║\n" "$GRPC_URL"
echo "╠══════════════════════════════════════════════════════════════════════╣"
echo "║  k6 modeli    : N connections, N concurrent (1 stream / bağlantı)   ║"
echo "║  Gerçek model : 1 connection, N concurrent  (N stream / bağlantı)   ║"
echo "║  Prod modeli  : K connections, N concurrent (N/K stream / bağlantı) ║"
echo "╠══════════════════════════════════════════════════════════════════════╣"
printf "║  Her senaryo süresi: %-49s║\n" "${DURATION}s"
printf "║  Pool boyutu (G4): %-51s║\n" "POOL_VU=${POOL_VU} (k6 S9 ile eşleşir)"
echo "╚══════════════════════════════════════════════════════════════════════╝"

# ── S1 eşdeğeri: GetProduct, küçük payload ───────────────────────────────────
echo ""
echo "━━━  G1 — GetProduct · 50 concurrent · ${DURATION}s  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  [k6 modeli: 50 ayrı TCP bağlantısı, sequential istek]"
run_ghz "g1_k6_model"  "product.ProductGrpcService.GetProduct" '{"id":1}' 50 50
echo "  [Gerçek HTTP/2 multiplexing: 1 bağlantı, 50 eş zamanlı stream]"
run_ghz "g1_multiplex" "product.ProductGrpcService.GetProduct" '{"id":1}'  1 50
echo "  [Üretim benzeri: 5 bağlantı, 50 concurrent (10 stream/bağlantı)]"
run_ghz "g1_pool_5"    "product.ProductGrpcService.GetProduct" '{"id":1}'  5 50

# ── S2 eşdeğeri: ListProducts, büyük payload ─────────────────────────────────
# Concurrency = 10: 10 × ~20MB = ~200MB direct buffer → 2GB limit içinde güvenli.
# Bu seviyede makine kısıtına değil, protokol farkına bakıyoruz.
# HoL ipucu: g2_multiplex >> g2_k6_model ise TCP/flow-control bağlantıyı boğuyor.
# g2_multiplex ≈ g2_k6_model ise darboğaz bağlantı sayısı değil, serialization CPU'su.
echo ""
echo "━━━  G2 — ListProducts · 10 concurrent · ${DURATION}s  ━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  [k6 modeli: 10 ayrı bağlantı — HoL mümkün değil (her yanıt kendi TCP'sinde)]"
run_ghz "g2_k6_model"  "product.ProductGrpcService.ListProducts" '{}' 10 10
echo "  [Gerçek multiplexing: 1 bağlantı, 10 stream — TCP/flow-control HoL riski]"
run_ghz "g2_multiplex" "product.ProductGrpcService.ListProducts" '{}'  1 10
echo "  [Üretim benzeri: 3 bağlantı, 10 concurrent — risk 1/3'e düşer]"
run_ghz "g2_pool_3"    "product.ProductGrpcService.ListProducts" '{}'  3 10

# ── G5: Head-of-Line Blocking (HoL) Testi ────────────────────────────────────
# Soru: Tek TCP bağlantısı üzerinden 20MB ListProducts akarken,
#       aynı bağlantıdan gelen küçük GetProduct istekleri gecikmeli yanıt alıyor mu?
#
# HoL'un HTTP/2 üzerindeki üç kaynağı:
#   1. HTTP/2 bağlantı flow-control penceresi (varsayılan 65535 B):
#      20MB veri pencereyi anında doldurur → istemci WINDOW_UPDATE gönderene kadar
#      tüm stream'ler (GetProduct dahil) yeni veri alamaz.
#   2. TCP send buffer satürasyonu:
#      Sunucu 20MB gönderirken TCP congestion window küçülür →
#      tüm stream frame'leri kuyrukta bekler.
#   3. Netty write backpressure:
#      Outbound buffer dolarsa Netty channel yazma işlemini durdurur →
#      GetProduct yanıtları da sıraya girer.
#
# Test yöntemi:
#   - Baseline (referans): g1_multiplex — GetProduct, 1 bağlantı, 50 stream, yük yok
#   - Yük altı: Arka planda sürekli 20MB ListProducts akışı (1 bağlantı, 5 stream)
#     VARKEN aynı sunucuya GetProduct (1 bağlantı, 50 stream) atılır.
#
# Yorum:
#   g5_get_under_hol p95 ≈ g1_multiplex p95  → HoL etkisi yok / HTTP/2 absorbe etti
#   g5_get_under_hol p95 >> g1_multiplex p95 → HoL görünür; üretimde tek bağlantı riski var
#
# Not: İki ayrı ghz prosesi iki ayrı TCP bağlantısı açar (ghz mixed-method desteklemez).
#      Bu nedenle HTTP/2 stream-seviyesindeki HoL yerine sunucu-taraflı Netty + network
#      bant genişliği baskısı ölçülür. Bağlantı-içi HoL için G2 g2_multiplex vs g2_k6_model
#      karşılaştırmasına bakın: orada 50 stream'in tamamı aynı TCP bağlantısını paylaşır.
#
HOL_LIST_CONNS=1
HOL_LIST_CONC=2    # 2 × ~20MB = ~40MB arka plan baskısı — sunucuyu çökertmeden HoL ölçer
HOL_GET_CONNS=1
HOL_GET_CONC=50

echo ""
echo "━━━  G5 — Head-of-Line Blocking Testi  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Soru: 20MB ListProducts akışı VARKEN GetProduct gecikiyor mu?"
echo "  Baseline referans: g1_multiplex (yukarıda) — yük yok, 1 bağlantı, 50 stream"
echo ""
echo "  [Adım 1: Arka plan yükü başlatılıyor — ${HOL_LIST_CONNS} bağlantı × ${HOL_LIST_CONC} eş zamanlı ListProducts]"
printf "    %-48s" "g5_hol_list_bg (background, ${DURATION+20}s)"

ghz --insecure \
    --proto    "$PROTO" \
    --call     "product.ProductGrpcService.ListProducts" \
    --data     '{}' \
    --connections "$HOL_LIST_CONNS" \
    --concurrency "$HOL_LIST_CONC" \
    --duration "$((DURATION + 20))s" \
    --format   json \
    --output   "$OUT_DIR/g5_hol_list_bg.json" \
    "$GRPC_URL" &>/dev/null &
HOL_BG_PID=$!

# ListProducts akışlarının başlaması ve TCP buffer'ı doldurmaya başlaması için bekle
sleep 5
echo "  başlatıldı (PID=$HOL_BG_PID)"

echo "  [Adım 2: GetProduct latency ölçülüyor — 20MB baskısı altında]"
run_ghz "g5_get_under_hol" "product.ProductGrpcService.GetProduct" '{"id":1}' "$HOL_GET_CONNS" "$HOL_GET_CONC"

wait "$HOL_BG_PID" 2>/dev/null || true

echo ""
echo "  Yorum:"
echo "    g5_get_under_hol p95 ≈ g1_multiplex p95  → HoL yok; HTTP/2 framing + Netty başarılı"
echo "    g5_get_under_hol p95 >> g1_multiplex p95 → HoL var; üretimde büyük/küçük payload"
echo "                                                karışımında tek bağlantı kullanma"

# ── G4: S9 REST Pool eşdeğeri — GetProduct, POOL_VU bağlantı ─────────────────
echo ""
echo "━━━  G4 — GetProduct · ${POOL_VU} connections · yüksek concurrent · ${DURATION}s  ━━━━━━━━━━━"
echo "  [k6 S9 REST karşılığı: ${POOL_VU} ayrı HTTP/1.1 bağlantı, sıralı]"
run_ghz "g4_k6_rest_model" "product.ProductGrpcService.GetProduct" '{"id":1}' "$POOL_VU" "$POOL_VU"
echo "  [Gerçek gRPC pool: ${POOL_VU} bağlantı, yüksek eş zamanlı stream]"
run_ghz "g4_grpc_pool"     "product.ProductGrpcService.GetProduct" '{"id":1}' "$POOL_VU" $((POOL_VU * 10))
echo "  [Tek bağlantı referansı: multiplexing üst sınırı]"
run_ghz "g4_single_conn"   "product.ProductGrpcService.GetProduct" '{"id":1}' 1          $((POOL_VU * 10))

# ── G6: Server Streaming — ListProducts stream modeli ────────────────────────
# Unary ListProducts (G2) ile karşılaştırma:
#   Unary  : Tüm 5000 ürün serialize → tek 20MB mesaj → gönder
#   Stream : Her ürün ayrı frame → ilk ürün hemen iletilir → TCP flow-control sorunsuz
#
# k6 gRPC client server-streaming desteği tam değil; ghz bunu ölçebilir.
# Beklenti: g6_stream p(95) << g2_k6_model p(95) — streaming time-to-first-response avantajı.
# NOT: ghz StreamProducts sonucu "tüm stream tamamlanma süresi" ölçer, TTFR değil.
#      TTFR için özel interceptor gerekir. Bu test toplam süre farkını ortaya koyar.
echo ""
echo "━━━  G6 — Server Streaming (StreamProducts) · ${DURATION}s  ━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Soru: Unary ListProducts vs Server Streaming — toplam süre ve RPS farkı nedir?"
echo "  Referans: G2 g2_k6_model ve g2_multiplex (yukarıda)"
echo ""
echo "  [Streaming, k6 modelinde: 10 ayrı bağlantı, 10 concurrent stream]"
run_ghz "g6_stream_k6"   "product.ProductGrpcService.StreamProducts" '{}' 10 10
echo "  [Streaming, tek bağlantı: 1 bağlantı, 10 concurrent stream]"
run_ghz "g6_stream_mux"  "product.ProductGrpcService.StreamProducts" '{}'  1 10
echo "  [Streaming, üretim pool: 3 bağlantı, 10 concurrent]"
run_ghz "g6_stream_pool" "product.ProductGrpcService.StreamProducts" '{}'  3 10
echo ""
echo "  Yorum:"
echo "    G6 RPS >> G2 RPS  → Streaming belirgin avantaj sağlıyor"
echo "    G6 RPS ≈  G2 RPS  → Darboğaz serialization CPU, framing değil"

# ── G7: gRPC Cache vs REST Cache — serializasyon maliyeti karşılaştırması ────
# k6 S11 (gRPC ListProductsCachedGrpc) ile S5/S6 REST cache arasındaki fark:
#   REST S5: Redis JSON bytes → Jackson 2× → HTTP JSON body
#   REST S6: Redis Proto bytes → parseFrom → Jackson → HTTP JSON body
#   gRPC S11: Redis Proto bytes → parseFrom → onNext() (Protobuf wire, tek dönüşüm)
#
# Bu test ghz ile gRPC cache path'ini yüksek concurrent'ta ölçer:
# k6 S11 ile karşılaştırıldığında ghz'nin open-loop modeli üst sınır throughput verir.
echo ""
echo "━━━  G7 — gRPC Cache (ListProductsCachedGrpc) · ${DURATION}s  ━━━━━━━━━━━━━━━━━━━━━"
echo "  Karşılaştırma: k6 S5 (REST JSON cache) vs S6 (REST Proto cache) vs G7 (gRPC cache)"
echo "  gRPC cache path: Redis Proto bytes → single parseFrom → onNext()"
echo ""
echo "  [k6 modeli: 10 bağlantı, 10 concurrent]"
run_ghz "g7_grpc_cache_k6"  "product.ProductGrpcService.ListProductsCachedGrpc" '{}' 10 10
echo "  [Multiplexing: 1 bağlantı, 10 concurrent]"
run_ghz "g7_grpc_cache_mux" "product.ProductGrpcService.ListProductsCachedGrpc" '{}'  1 10

# ── G3: Yüksek eşzamanlılık stres testi — EN SONDA çalışır ───────────────────
# G3 kasıtlı olarak en sona bırakıldı: 30 concurrent × ~20MB = ~600MB direct buffer.
# Bu seviyede sunucu CPU ve memory'de zorlanmaya başlar.
# G3 sonrası sunucu degraded state'e girebilir — diğer testleri zehirlemez.
# Hata oranı yüksekse: darboğaz serializasyon CPU'su (protokol değil makine limiti).
# Üretim makinesinde (8+ vCPU) bu senaryo temiz çalışmalı.
echo ""
echo "━━━  G3 — ListProducts · 30 concurrent · ${DURATION}s  [Stres — en sonda]  ━━━━━━━━━"
echo "  [k6 modeli: 30 ayrı bağlantı]"
run_ghz "g3_k6_model"   "product.ProductGrpcService.ListProducts" '{}' 30 30
echo "  [Gerçek multiplexing: 1 bağlantı, 30 stream]"
run_ghz "g3_multiplex"  "product.ProductGrpcService.ListProducts" '{}'  1 30
echo "  [Üretim benzeri: 5 bağlantı, 30 concurrent (6 stream/bağlantı)]"
run_ghz "g3_pool_5"     "product.ProductGrpcService.ListProducts" '{}'  5 30

# ── Özet ──────────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Ham JSON sonuçlar: $OUT_DIR/"
echo ""
echo "  k6 ↔ ghz karşılaştırma rehberi:"
echo "    k6 S7  (REST MaxTP)    ↔  G1 g1_multiplex   → Adil REST vs gRPC throughput"
echo "    k6 S9  (REST Pool)     ↔  G4 g4_grpc_pool   → Pool boyutunda adil karşılaştırma"
echo "    k6 S2  (büyük payload) ↔  G2 + G5 HoL       → Multiplexing + HoL analizi"
echo "    k6 S2  (unary)         ↔  G6 (streaming)    → Unary vs Server Streaming"
echo "    k6 S5/S6 (REST cache)  ↔  G7 (gRPC cache)   → Serializasyon maliyet karşılaştırması"
echo ""
echo "  G2 — Büyük payload multiplexing yorumu:"
echo "    g2_k6_model << g2_multiplex → HoL: TCP/flow-control bağlantıyı boğuyor"
echo "    g2_k6_model ≈  g2_multiplex → Darboğaz bağlantı sayısı değil, serialization"
echo "    g2_k6_model >> g2_multiplex → Multiplexing kazanıyor (bağlantı kurma pahalıydı)"
echo ""
echo "  G5 — Head-of-Line Blocking yorumu:"
echo "    g5 p95 ≈ g1_multiplex p95  → HoL yok; HTTP/2 frame interleaving + Netty başarılı"
echo "    g5 p95 >> g1_multiplex p95 → HoL var; büyük+küçük payload mix'i için:"
echo "                                  pool_5 / pool_10 gibi çok-bağlantılı model kullan"
echo ""
echo "  G6 — Server Streaming yorumu:"
echo "    G6 RPS >> G2 RPS  → Streaming belirgin avantaj; büyük liste için StreamProducts kullan"
echo "    G6 RPS ≈  G2 RPS  → Darboğaz CPU serialization; framing overhead kayda değer değil"
echo ""
echo "  G7 — gRPC Cache yorumu:"
echo "    G7 p95 < k6-S6 p95  → gRPC cache path daha hızlı (single deserialization avantajı)"
echo "    G7 p95 ≈ k6-S6 p95  → Darboğaz Redis RTT veya CPU; serializasyon tipi önemsiz"
echo ""
echo "  G1/G2/G3 — Multiplexing yorumu:"
echo "    k6_model ≈ multiplex  → Multiplexing fark yaratmıyor (darboğaz ağ/serialization)"
echo "    k6_model > multiplex  → Daha az bağlantıyla daha yüksek verim; HTTP/2 avantajı"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

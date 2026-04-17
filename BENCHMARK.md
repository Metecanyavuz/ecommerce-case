# gRPC vs REST Benchmark — Senaryo Rehberi

Bu döküman, `benchmark/` klasöründeki k6 testlerinin ne yaptığını, hangi koşulları simüle ettiğini ve her senaryodan beklenen sonuçları açıklar.

---

## Mimari

```
k6-runner (EC2-B)
    │
    ├── REST  → HTTP/1.1 + JSON + Keep-Alive → product-service:8083
    └── gRPC  → HTTP/2 + Protobuf            → product-service:9090
                                                      │
                                               PostgreSQL (ecommerce / products schema)
                                               Redis (database: 10)
                                               5000 ürün, ~3.9 KB description / ürün
```

İki makine aynı VPC içinde çalışır — karşılaştırma adildir, proxy katmanı yoktur.

---

## Adil Karşılaştırma İçin Alınan Kararlar

### HTTP Keep-Alive (REST)

REST isteklerinde `Connection: keep-alive` açıkça belirtilmiştir. Bu hem gerçekçidir (tüm modern HTTP istemciler Keep-Alive kullanır) hem de adildir — her protokol kendi optimal bağlantı stratejisini kullanmaktadır.

| | REST (HTTP/1.1 + Keep-Alive) | gRPC (HTTP/2) |
|---|---|---|
| Bağlantı yeniden kullanımı | ✓ | ✓ |
| Eşzamanlı istek / bağlantı | ✗ 1 istek | ✓ Multiplexing |
| 50 VU için bağlantı sayısı | 50 adet | Teorik: 1 yeterli |

### Senaryo Sırası

```
warmup → S1 → S2 → S5/S6(cache) → S3 → [60s soğuma] → S4(spike) → [120s recovery] → S7/S8 → S9/S10
```

- **Cache (S5/S6)** S2 ile benzer makine durumunda çalışır — spike öncesi
- **60s soğuma** S3 stress testinden kalan baskının dağılmasını sağlar
- **120s recovery (GAP2)** S4 cold spike sonrası Netty thread pool ve OS buffer'ının tam temizlenmesi için

### S4 Cold Spike — Bilinen Sınırlılık ve Tasarım Kararı

S4'te gRPC, cold spike koşulunda REST'e göre dramatik biçimde yavaş görünür. Sebep: 100 VU eş zamanlı HTTP/2 handshake yapar; Netty (2 vCPU × 2 = 4 worker thread) bu isteği karşılayamaz, backpressure oluşur. Üretim ortamında connection pool bu farkı kapatır. Ham cold-start davranışı **intentional olarak** ölçülmektedir. S9/S10 ise warm pool modelini ölçer.

### GAP2 = 120s — S4 Recovery Penceresi

Çalıştırma 3'te `GAP_W=30s` yetmedi: S4 cold spike Netty'yi ezdi, S8 ve S4b tamamen başarısız oldu (`DeadlineExceeded`, `Canceled`). 120 saniye:
- OS TIME_WAIT kapanır (2 × MSL = 2 × 60s = 120s)
- Netty backpressure queue drene olur
- S7/S8/S9/S10 temiz sunucuyla çalışır

### S4b Kaldırıldı (Çalıştırma 3 → 4)

S4b (warm spike, 50 VU) her çalıştırmada S4 cold spike hasarı yüzünden %100 hata veriyordu. Aynı kavram (pre-established connection pool) S10 tarafından daha doğru ölçülüyor:
- S4b: VU-based concurrency, warmReady flag — yaklaşık simülasyon
- S10: `ramping-arrival-rate` + `preAllocatedVUs=10` — gerçek pool modeli

### S8 maxVUs: 300 → 50

Eski değer (300): hedef RPS tutturulamazsa k6 300 VU spawn eder, hepsi timeout'a girer → `DeadlineExceeded` seli. Yeni değer (50): 200 RPS için yeterli kapasiteden yüksek; tutturulamazsa dropped iteration görünür, zombi VU oluşmaz.

---

## k6 gRPC Sınırlılığı — Multiplexing Ölçülemiyor

### Sorunun Özü

k6'da her VU **izole bir JavaScript runtime**'dır. `client.invoke()` bloklayıcıdır (synchronous) — cevap gelmeden bir sonraki satıra geçilmez. Bu nedenle her VU, bağlantısı üzerinde aynı anda yalnızca **1 aktif stream** çalıştırır.

```
Gerçek üretim (1 ManagedChannel, 50 goroutine):
  TCP bağlantısı ─┬─ Stream 1 → istek uçuşta
                  ├─ Stream 3 → istek uçuşta
                  ├─ Stream 5 → istek uçuşta
                  └─ Stream 7 → istek uçuşta
  → 1 bağlantı, 50 eş zamanlı RPC

k6 (50 VU, her birinde ayrı grpc.Client()):
  TCP bağlantısı-1 ── Stream 1 → bekle → cevap → bekle ...
  TCP bağlantısı-2 ── Stream 1 → bekle → cevap → bekle ...
  ...
  TCP bağlantısı-50 ─ Stream 1 → bekle → cevap → bekle ...
  → 50 bağlantı, her birinde 1 stream (sıralı)
```

### "Bir VU'ya Birden Fazla Channel Açılabilir" mi?

Teknik olarak evet — ama işe yaramaz. VU single-thread olduğu için iki channel açılsa da hâlâ sıralı kullanılır:

```javascript
const client1 = new grpc.Client();
const client2 = new grpc.Client();
// ...
const res1 = client1.invoke('...', {}); // BEKLE
const res2 = client2.invoke('...', {}); // res1 bittikten sonra başlar
```

Sonuç: 2 TCP bağlantısı, hâlâ sıralı kullanım. Multiplexing değil.

### k6'da S9/S10'un Gerçekte Ölçtüğü Şey

S9/S10 (`ramping-arrival-rate`, `preAllocatedVUs=10`) şu soruyu yanıtlar:
> **"10 bağlantıyla kaç RPS sürdürülebilir, ve REST ile gRPC bu noktada nasıl ayrışır?"**

gRPC avantajı görünürse bunun kaynağı multiplexing değil, **daha düşük per-request latency**dir (S1'de ölçülen ~%17 fark). Daha az latency → aynı VU sayısıyla daha fazla RPS.

### Gerçek Multiplexing İçin: ghz

`ghz`, Go'nun goroutine modeli sayesinde tek bir TCP bağlantısı üzerinden gerçek anlamda N eş zamanlı stream gönderebilir. Bu k6'nın gösteremediği HTTP/2 multiplexing avantajını ölçer.

```
ghz --connections 1 --concurrency 50
  → 1 TCP bağlantısı + 50 eş zamanlı gRPC stream (HTTP/2 spec'e uygun)

ghz --connections 50 --concurrency 50
  → k6'nın davranışını taklit eder (50 ayrı bağlantı, her birinde 1 stream)
```

Karşılaştırma: `ghz-compare.sh` (aşağıda) her senaryoyu her iki modda çalıştırır — fark görünürse k6'nın bu senaryoyu eksik ölçtüğü anlaşılır.

### Araç Karşılaştırması

| | k6 | ghz |
|---|---|---|
| Multiplexing (N stream, 1 bağlantı) | ✗ | ✓ |
| Protokol karışık test (REST + gRPC) | ✓ | ✗ (yalnızca gRPC) |
| Senaryo sıralaması / zaman planı | ✓ | ✗ |
| Cache / Redis testi | ✓ | ✗ |
| Spike / arrival-rate senaryoları | ✓ | Sınırlı |
| Kullanım amacı | Tam sistem karşılaştırması | gRPC multiplexing doğrulaması |

---

## Bağımsız ve Bağımlı Değişkenler

### Bağımsız Değişkenler

| Değişken | Değerler |
|----------|---------|
| Protokol | REST (HTTP/1.1 + JSON) / gRPC (HTTP/2 + Protobuf) |
| Payload boyutu | ~300 bytes (1 ürün) / ~20 MB (5000 ürün) |
| Eşzamanlı kullanıcı | 50 VU / 200 VU / 0→100 VU (spike) |
| Cache durumu | Soğuk (DB'den) / Sıcak (Redis'ten) |
| RPS hedefi | 200 RPS (S7/S8/S9/S10, env var ile ayarlanabilir) |

### Bağımlı Değişkenler

| Metrik | Açıklama |
|--------|----------|
| `avg` | Ortalama istek süresi — tek başına yanıltıcı olabilir |
| `p(95)` | İsteklerin %95'i bu süreden kısa — SLA tanımında kullanılan standart |
| `p(99)` | Uç vakaları gösterir — spike senaryosunda p(95)'ten daha anlamlı |
| `max` | En yavaş istek — outlier davranışını ortaya koyar |
| `rest_s2_bytes_per_req` | S2 per-request JSON body boyutu (wire-accurate, headers hariç) |
| `grpc_s2_bytes_approx` | S2 per-request Protobuf yaklaşımı (JSON.stringify overestimate) |
| Hata oranı | Sistemin dayanıklılığı |

### Network Ölçümü — Doğruluk Notu

**REST** `res.body.length`: Response body için wire-accurate (~%100). HTTP headers (~500B/istek) dahil değil — 20 MB payload için önemsiz (%0.002).

**gRPC** `JSON.stringify(parsed_message).length`: k6 raw Protobuf bytes expose etmez; mesaj deserialize edildikten sonra JSON boyutu ölçülür. Bu bir **üst sınır tahmini**. Gerçek Protobuf wire size daha küçüktür; ancak bu veri setinde ürün açıklamaları uzun string olduğundan fark sınırlıdır (~%10-20). Kesin ölçüm için server-side Micrometer/Prometheus bytes_sent veya tcpdump gerekir.

**Önceki hata:** Tüm senaryoların cumulative Counter'ı tutuluyordu (S1+S2+S3+...+S9 toplamı). Farklı senaryo iterasyon sayıları nedeniyle REST/gRPC karşılaştırması anlamsızdı. Düzeltme: Sadece S2 büyük payload için per-request `Trend` metriği.

---

## Senaryolar

### Warmup — JVM Ön Isınma

| | |
|--|--|
| **Endpoint** | `GET /products/1` |
| **VU** | 5 |
| **Süre** | 30 saniye |

JVM'in JIT compilation yapması ve bağlantı havuzunun oluşması için. Bu olmadan ilk senaryo soğuk JVM dezavantajıyla başlar.

---

### S1 — Küçük Payload, Normal Yük

| | |
|--|--|
| **Endpoint** | `GET /products/1` / `GetProduct(id=1)` |
| **Payload** | ~300 bytes (1 ürün) |
| **VU** | 50 |
| **Süre** | 115 saniye (10s ramp → 30s → 60s sustained → 10s down) |

**Ne simüle eder:** Kullanıcının ürün detay sayfasını açması. Order Service'in sipariş doğrulamak için tek ürün sorgusu atması.

**Neyi ölçer:** Payload küçük olduğundan serialization maliyeti sıfıra yakın. Görünen fark tamamen **saf protokol overhead'i** — HTTP/1.1 vs HTTP/2 header boyutu, bağlantı maliyeti.

**Beklenen:** İki protokol çok yakın. REST hafif avantajlı olabilir — Spring MVC'nin senkron pipeline'ı küçük istekler için optimize.

---

### S2 — Büyük Payload, Normal Yük

| | |
|--|--|
| **Endpoint** | `GET /products` / `ListProducts()` |
| **Payload** | ~20 MB (5000 ürün) |
| **VU** | 50 |
| **Süre** | 115 saniye |

**Ne simüle eder:** Admin panelinin tüm ürün katalogunu yüklemesi. Backend servisler arası toplu veri transferi.

**Neyi ölçer:** Asıl **serialization savaşı**. Spring Boot 5000 ürünü JSON'a çevirirken GC baskısı başlar. gRPC binary Protobuf ile daha küçük boyutta gönderir. S2 per-request byte ölçümü ile protokol wire size farkı görülür.

**Beklenen:** gRPC öne çıkar. Hem latency düşer hem bandwidth avantajı görülür.

---

### S5/S6 — Redis Cache (Warm)

| | |
|--|--|
| **Endpoint** | `GET /products/cached-json` / `GET /products/cached-proto` |
| **Payload** | ~20 MB (5000 ürün, Redis'ten) |
| **VU** | 50 |
| **Süre** | 110 saniye |
| **Cache durumu** | Warm — `setup()` test başlamadan cache'i ısıtır |

**Ne simüle eder:** Sıkça erişilen katalog verisinin Redis'ten sunulması.

**Neyi ölçer:**
- **S5:** Redis'ten JSON olarak cache'lenmiş listeyi okuyup döndürme
- **S6:** Redis'ten Protobuf binary olarak cache'lenmiş listeyi okuyup JSON'a çevirip döndürme
- Her ikisinin S2 (no-cache) ile karşılaştırılması cache gain'i gösterir

**Bilinen sınırlılık — Double Serialization:**
Cache HIT yolunda `ProductCacheService` double serialization yapıyor:
```
Redis GET → 20 MB bytes → ObjectMapper.readValue() → List<ProductResponse>
                                                             ↓ Spring MVC
                                                  ObjectMapper.writeValue() → HTTP
```
DB yolu ise tek serialize. Bu yapısal sorun cache'i no-cache'ten daha yavaş yapabilir. Gerçek kazanç `ResponseEntity<byte[]>` ile raw bytes döndürülerek elde edilebilir.

**Sıralama notu:** Bu testler S3/S4 spike'larından önce çalışır — adil koşullar.

---

### S3 — Büyük Payload, Yüksek Eşzamanlılık

| | |
|--|--|
| **Endpoint** | `GET /products` / `ListProducts()` |
| **Payload** | ~20 MB (5000 ürün) |
| **VU** | 200 |
| **Süre** | 75 saniye (10s → ramp 200 → 30s sustained → 10s down) |

**Ne simüle eder:** Mesai başlangıcında tüm operasyon ekibinin admin panelini açması.

**Neyi ölçer:** S2'deki serialization farkının yüksek concurrency altında nasıl büyüdüğü. HTTP/2 multiplexing burada devreye girer.

**Beklenen:** S2'ye kıyasla iki protokol arasındaki makas açılır.

---

### S4 — Flash Sale Spike (Cold)

| | |
|--|--|
| **Endpoint** | `GET /products/1` / `GetProduct(id=1)` |
| **Payload** | ~300 bytes (1 ürün) |
| **VU** | 0 → `MAX_SPIKE_VU` (default: 100), 10 saniyede |
| **Süre** | 55 saniye (10s spike → 30s sustained → 10s down) |
| **Spike öncesi** | 60s soğuma (S3 sonrası) |

**Ne simüle eder:** Flash sale açılışı. Sistem saniyeler içinde normal kapasitesinin üzerine çıkar.

**Neyi ölçer:** Ani bağlantı baskısı altında protokol dayanıklılığı. Her VU ilk iterasyonda bağlantı kurar — **connection establishment overhead'i kasıtlı olarak görünür**.

**Bilinen sınırlılık:** Cold spike gRPC'yi orantısız yavaşlatır (100 eş zamanlı HTTP/2 handshake vs HTTP/1.1 TCP). Bu üretim koşulunu temsil etmez. Üretim pool modeli için S10'a bakın.

**Sonrası:** 120s GAP2 — Netty ve OS tamamen toparlanır, S7/S8/S9/S10 temiz başlar.

---

### S7 — REST Max Throughput (Darboğaz Bulucu)

| | |
|--|--|
| **Endpoint** | `GET /products/1` |
| **Executor** | `ramping-arrival-rate` |
| **RPS hedefi** | `MAX_RPS × [5%, 25%, 50%, 100%]` (default: 10→50→100→200) |
| **Süre** | ~110 saniye |
| **maxVUs** | 300 |

**Ne ölçer:** REST'in sürdürebileceği maksimum RPS. HTTP/1.1 doğal modelinde test edilir: daha fazla RPS = daha fazla connection. Hata oranı %5'i geçtiğinde darboğaz aşılmıştır.

**Adil mı?** Evet — REST production'da da bu şekilde ölçeklenir.

---

### S8 — gRPC Throughput [k6 sequential modeli — alt sınır]

| | |
|--|--|
| **Endpoint** | `GetProduct(id=1)` |
| **Executor** | `ramping-arrival-rate` |
| **RPS hedefi** | `MAX_RPS × [5%, 25%, 50%, 100%]` |
| **Süre** | ~110 saniye |
| **maxVUs** | 50 |

**Ne ölçer:** gRPC'nin k6'nın sequential modelinde (1 stream/bağlantı) sürdürebildiği RPS.

**Adil mı? HAYIR — gRPC için alt sınır ölçümüdür.**

k6'da `maxVUs=50` → maksimum 50 eş zamanlı stream. Production'da 50 gRPC channel, multiplexing ile 500+ eş zamanlı RPC karşılayabilir. k6 bu avantajı gösteremiyor.

**S7 ile karşılaştırma:** S7 REST'i doğal modelinde, S8 gRPC'yi kısıtlanmış modelinde test eder. S7 vs S8 karşılaştırması **gRPC aleyhine haksızdır**. Gerçek gRPC throughput için `ghz-compare.sh` G1 sonuçlarına bakın.

**maxVUs=50 neden?** Çalıştırma 3'te maxVUs=300 ile S4 sonrası yorgun sunucuya 300 zombi VU çarptı; tüm istekler `DeadlineExceeded` verdi. 50 ile dropped iteration görünür, zombi VU seli olmaz.

---

### S9 — REST Connection Pool Simülasyonu

| | |
|--|--|
| **Endpoint** | `GET /products/1` |
| **Executor** | `ramping-arrival-rate` |
| **Pool boyutu** | `MS_POOL_VU` VU (default: 10) |
| **RPS hedefi** | `MS_MAX_RPS` (default: 200) |
| **Süre** | ~80 saniye |

**Ne ölçer:** 10 keep-alive bağlantıyla REST'in karşılayabildiği RPS ve latency. REST doğal modelinde test edilir.

**Adil mı?** Evet — HTTP/1.1 zaten sequential, k6 modeli production gerçeğini yansıtır.

---

### S10 — gRPC Connection Pool [k6 sequential modeli — alt sınır]

| | |
|--|--|
| **Endpoint** | `GetProduct(id=1)` |
| **Executor** | `ramping-arrival-rate` |
| **Pool boyutu** | `MS_POOL_VU` VU (default: 10) |
| **RPS hedefi** | `MS_MAX_RPS` (default: 200) |
| **Süre** | ~80 saniye |

**Ne ölçer:** 10 sequential gRPC channel'ın karşılayabildiği RPS.

**Adil mı? HAYIR — gRPC için alt sınır ölçümüdür.**

Production'da 10 gRPC channel, multiplexing ile S9'daki 10 REST bağlantısının çok üzerinde RPS karşılar. k6'da her VU sequential olduğu için bu avantaj görünmez.

**S9 ile karşılaştırma:** S9 REST'i, S10 gRPC'yi k6 kısıtlamasıyla test eder. Fark varsa kaynağı multiplexing değil, per-request latency farkıdır (S1'de ölçülen ~%17). Gerçek karşılaştırma için `ghz-compare.sh` G1 (--connections=10 --concurrency=200) sonuçlarına bakın.

---

## Env Değişkenleri

| Değişken | Default | Açıklama |
|----------|---------|----------|
| `REST_URL` | `http://localhost:8083` | REST endpoint |
| `GRPC_URL` | `localhost:9090` | gRPC endpoint |
| `MAX_SPIKE_VU` | `100` | S4 cold spike max VU |
| `MAX_RPS` | `200` | S7/S8 max throughput hedefi |
| `MS_POOL_VU` | `10` | S9/S10 sabit pool boyutu |
| `MS_MAX_RPS` | `200` | S9/S10 RPS hedefi |

---

## Senaryo Özet Tablosu

| | S1 | S2 | S5/S6 | S3 | S4 | S7 | S8 | S9 | S10 |
|--|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| Protokol | REST+gRPC | REST+gRPC | REST | REST+gRPC | REST+gRPC | REST | gRPC | REST | gRPC |
| Payload | ~300 B | ~20 MB | ~20 MB | ~20 MB | ~300 B | ~300 B | ~300 B | ~300 B | ~300 B |
| Max VU/RPS | 50 VU | 50 VU | 50 VU | 200 VU | 100 VU | 200 RPS | 200 RPS | 200 RPS | 200 RPS |
| Süre | 115s | 115s | 110s | 75s | 55s | 110s | 110s | 80s | 80s |
| gRPC adil mi? | ✓ | ✓ | ✓ | Kısmi | ✓ | — | ⚠ Alt sınır | — | ⚠ Alt sınır |
| Ana ölçüm | Protokol overhead | Serialization | Cache gain | Concurrency | Cold-start | REST throughput | gRPC throughput (k6) | REST pool | gRPC pool (k6) |

**✓ = Her iki protokol için adil · ⚠ = gRPC k6 sequential kısıtı altında (gerçek kapasite daha yüksek)**

**Toplam süre: ~25 dakika (k6) + ~10 dakika (ghz, opsiyonel)**

---

## Çalıştırma

### AWS (İki Makine) — k6

```bash
# EC2-B (k6 makinesi) üzerinde
REST_URL=http://<EC2-A-PRIVATE-IP>:8083 \
GRPC_URL=<EC2-A-PRIVATE-IP>:9090 \
bash ~/benchmark/run-aws.sh | tee ~/benchmark/results.txt
```

Özel parametrelerle:

```bash
REST_URL=http://<IP>:8083 \
GRPC_URL=<IP>:9090 \
MAX_SPIKE_VU=100 \
MAX_RPS=200 \
MS_POOL_VU=10 \
MS_MAX_RPS=200 \
bash ~/benchmark/run-aws.sh | tee ~/benchmark/results.txt
```

### AWS (EC2-B) — ghz Multiplexing Testi

k6 benchmark'ından sonra (product-service ısınmış durumdayken) çalıştırılır.

**Kurulum (ilk kez):**
```bash
wget https://github.com/bojand/ghz/releases/latest/download/ghz_Linux_x86_64.tar.gz
tar -xzf ghz_Linux_x86_64.tar.gz
sudo mv ghz /usr/local/bin/
rm ghz_Linux_x86_64.tar.gz
sudo apt-get install -y jq   # JSON parse için
```

**Çalıştırma:**
```bash
GRPC_URL=<EC2-A-PRIVATE-IP>:9090 \
bash ~/benchmark/ghz-compare.sh | tee ~/benchmark/ghz-results.txt
```

**Ne yapıyor:**
Her senaryo için 3 mod çalışır (her biri 60s):
- `k6 modeli`: N bağlantı, N concurrent (k6'yı taklit eder)
- `gerçek multiplexing`: 1 bağlantı, N concurrent stream
- `üretim benzeri`: K bağlantı, N concurrent (K < N)

Sonuçlar `benchmark/ghz-results/` dizinine JSON olarak kaydedilir.

**Tahmini ek süre:** ~10 dakika (3 senaryo × 3 mod × 60s)

**Önerilen AWS instance boyutları:**

| Servis | Instance | Neden |
|--------|----------|-------|
| k6-runner (EC2-B) | `t3.medium` veya `c6a.large` | 100 VU + arrival-rate için yeterli |
| product-service (EC2-A) | `c6a.large` (2 vCPU, 8 GB) | Tomcat/Netty thread pool davranışı gözlemlenir |

**Gerekli Security Group kuralları (EC2-A — product-service):**

| Port | Kaynak |
|------|--------|
| 8083 | EC2-B private IP |
| 9090 | EC2-B private IP |

---

## Gerçek Sonuçlar

### Çalıştırma 1 — 2026-04-16 · gRPC max message size = 4 MB (default)

**Ortam:** Lokal makine · 500 VU spike · 5000 ürün (~20 MB)

```
╔══════════════════════════════════════════════════════════════════════╗
║  REST hata: %0.02   gRPC hata: %2.87   Cache hata: %0.00            ║
╚══════════════════════════════════════════════════════════════════════╝

S1  avg    REST: 3.0ms    gRPC: 5.9ms    → REST %95 daha hızlı
    p(95)  REST: 4.0ms    gRPC: 5.0ms    → REST %26 daha hızlı

S2  avg    REST: 6120ms   gRPC: 11227ms  → REST %83 daha hızlı   ⚠ beklenenin tersi
    p(95)  REST: 7941ms   gRPC: 26496ms

S3  avg    REST: 20216ms  gRPC: 22774ms  → REST %13 daha hızlı

S4  avg    REST: 56ms     gRPC: 10682ms  → REST %18823 daha hızlı (cold spike)

S5  p(95)  JSON  Cache: 37514ms  → No-Cache kıyasında %372 DAHA YAVAŞ  ⚠
S6  p(95)  Proto Cache: 37197ms  → No-Cache kıyasında %368 DAHA YAVAŞ  ⚠

S7  REST  p(95): 69.6ms   hata: %0.00
S8  gRPC  p(95): 33064ms  hata: %100.00  ⚠
```

**Kök Neden — gRPC S2/S3 yavaş ve S8 %100 hata:**
gRPC default max message size **4 MB**. 5000 ürün × ~3.9 KB = ~19.5 MB → limit 5x aşılıyor.

**Kök Neden — Cache %372 yavaş:**
`ProductCacheService` cache HIT yolunda double serialization yapıyor:
`Redis GET → ObjectMapper.readValue() → List → ObjectMapper.writeValue() → HTTP`
DB yolu tek serialize. 20 MB payload'da bu 2. Jackson geçişi DB'den daha pahalı.

---

### Çalıştırma 2 — 2026-04-16 · gRPC max message size = 20 MB + S4b eklendi

**Ortam:** AWS EC2 · `c6a.large` product-service · `c6a.2xlarge` k6-runner · 500 VU spike

`application.yaml`'a `max-inbound-message-size: 20971520` eklendi ve Docker rebuild yapıldı.

```
╔══════════════════════════════════════════════════════════════════════╗
║  REST hata: %0.01   gRPC hata: %3.56   Cache hata: %0.00            ║
╚══════════════════════════════════════════════════════════════════════╝

S1  avg    REST: 3.5ms    gRPC: 3.5ms    → ≈ eşit
    p(95)  REST: 5.8ms    gRPC: 5.0ms    → gRPC %14 daha hızlı  ✓

S2  avg    REST: 6601ms   gRPC: 5363ms   → gRPC %19 daha hızlı  ✓ (Çalıştırma 1'de REST öndeydı)
    p(95)  REST: 8720ms   gRPC: 7998ms   → gRPC %8 daha hızlı

S3  avg    REST: 18176ms  gRPC: 17754ms  → ≈ eşit
    p(95)  REST: 31379ms  gRPC: 26455ms  → gRPC %16 daha hızlı  ✓

S4 cold  p(95)  REST: 175ms    gRPC: 31681ms  → REST %18000+ daha hızlı (cold connection)

S4b warm p(95): 1ms  hata: %100  ⚠ S4 cold bitince hemen başlıyor — gRPC toparlanamıyor

S5  p(95)  JSON  Cache:  9126ms → No-Cache kıyasında %5 yavaş  (Çalıştırma 1: %372)
S6  p(95)  Proto Cache:  9527ms → No-Cache kıyasında %9 yavaş

S7  REST  p(95): 241ms    hata: %0.00
S8  gRPC  p(95): 20001ms  hata: %100.00  ⚠
```

**Bulgular:**
- gRPC 20MB limit fix → S2/S3'te gRPC artık beklenen şekilde REST'ten hızlı ✓
- Cache: Çalıştırma 1'deki %372 yavaşlama %5-9'a indi — makine durumu farkı; double serialization hala var
- S4b: araya GAP konmadan gRPC %100 hata → TIME_WAIT ve Netty backpressure
- S8: S4 hasarından toparlanamıyor → %100 hata

---

### Çalıştırma 3 — 2026-04-16 · GAP_W=30s + S7/S8 eklendi

**Ortam:** AWS EC2 · `c6a.large` product-service · `c6a.2xlarge` k6-runner · 100 VU spike · MAX_RPS=200

`GAP_W=30s` (S4 cold → S4b warm arası soğuma), `S7/S8` max throughput senaryoları eklendi.

```
╔══════════════════════════════════════════════════════════════════════╗
║  Spike MAX VU: 100    Max RPS: 200                                   ║
║  REST hata: %0.02   gRPC hata: %3.59   Cache hata: %0.00            ║
╚══════════════════════════════════════════════════════════════════════╝

S1  avg    REST: 3.6ms    gRPC: 3.5ms    → gRPC %5 daha hızlı
    p(95)  REST: 6.1ms    gRPC: 5.0ms    → gRPC %17 daha hızlı

S2  avg    REST: 6321ms   gRPC: 5274ms   → gRPC %17 daha hızlı
    p(95)  REST: 8396ms   gRPC: 7956ms   → gRPC %5 daha hızlı

S3  avg    REST: 17840ms  gRPC: 17506ms  → ≈ eşit
    p(95)  REST: 31188ms  gRPC: 26707ms  → gRPC %14 daha hızlı

S4 cold  avg    REST: 3.3ms      gRPC: 14124ms  → REST %434367 daha hızlı
         p(95)  REST: 5.2ms      gRPC: 20286ms

S4b warm p(95): 20210ms  hata: %100.00  ⚠ GAP_W=30s yetmedi

S5  p(95)  JSON  Cache:  10621ms → No-Cache (8396ms) kıyasında %27 yavaş
S6  p(95)  Proto Cache:   7491ms → No-Cache (8396ms) kıyasında %11 hızlı  ✓

S7  REST  p(95): 3.0ms    hata: %0.00
S8  gRPC  p(95): 59598ms  hata: %100.00  ⚠ (maxVUs=300, sunucu S4+S4b hasarından toparlanamadı)
```

**Bulgular:**
- S1/S2/S3: gRPC tutarlı biçimde öne geçti ✓
- S5 JSON cache: double serialization yüzünden no-cache'ten %27 yavaş (S6 proto: %11 hızlı)
- S4b ve S8: `GAP_W=30s` + S4b'nin sunucuyu ayrıca yıpratması → iki senaryo da tamamen başarısız
- **Kök Neden:** S4 cold spike Netty'yi eziyor; S4b 50 VU daha ekliyor; S8 başlayana kadar sunucu ölüyor
- `Canceled | Server sendMessage() failed` + `DeadlineExceeded` = Netty worker queue dolu

**S4b kaldırıldı:** S4b her çalıştırmada S4 hasarından dolayı anlamsız veri üretiyor. S10 (pool simülasyonu) aynı kavramı daha doğru ölçüyor.

---

## ghz Analiz Raporu — gRPC Multiplexing Gerçekte Ne Kadar İşe Yarıyor?

> **Ortam:** EC2-A (product-service, 2 vCPU) · EC2-B (ghz runner) · Aynı VPC · Her senaryo 60s

k6'da her VU bağımsız bir gRPC channel açar ve istekleri sıralı gönderir: 50 VU = 50 TCP bağlantısı, her birinde 1 aktif stream. HTTP/2'nin temel vaadi olan "tek bağlantıda N eş zamanlı stream" bu modelde hiçbir zaman gerçekleşmez. ghz'nin Go goroutine modeli bu kısıtı aşar; `--connections 1 --concurrency 50` ile gerçekten 1 TCP bağlantısı üzerinde 50 HTTP/2 stream eş zamanlı uçuşta olur.

**Test parametreleri:**

| Senaryo | Method | connections | concurrency | k6 karşılığı |
|---|---|---|---|---|
| G1 k6 modeli | GetProduct | 50 | 50 | S1 / S7 |
| G1 multiplexing | GetProduct | 1 | 50 | — (k6 ölçemiyor) |
| G2 k6 modeli | ListProducts (~20MB) | 10 | 10 | S2 |
| G2 multiplexing | ListProducts (~20MB) | 1 | 10 | — |
| G5 HoL | GetProduct (bg: ListProducts) | 1 | 50 | — |
| G4 REST modeli | GetProduct | 10 | 10 | S9 |
| G4 gRPC pool | GetProduct | 10 | 100 | — (k6 ölçemiyor) |
| G3 stres | ListProducts (~20MB) | 1/30 | 30 | S3 |

---

### Bulgu 1 (G1) — Küçük payload: TCP handshake gürültüsü multiplexing ile yok oluyor

`GetProduct` (~300 B yanıt), 50 eş zamanlı istemciyle:

| Model | `--conn` | `--conc` | Ortalama | p95 | RPS |
|---|---|---|---|---|---|
| k6 modeli | 50 | 50 | 24.2ms | 49.0ms | 2058 |
| Gerçek multiplexing | 1 | 50 | 11.6ms | **20.9ms** | **4267** |
| Üretim havuzu | 5 | 50 | 11.9ms | 23.8ms | 4162 |

Multiplexing modeli **2× daha fazla RPS**, **%57 daha düşük p95** üretiyor. Farkın kaynağı şu: k6 modelinde her istek kendi TCP bağlantısını kullanır. Bir TCP bağlantısının kurulması; TCP SYN/SYN-ACK/ACK (1 RTT) + TLS handshake (1-2 RTT) + HTTP/2 `SETTINGS` frame değiş-tokuşu (1 RTT) gerektirir. Yanıt yalnızca ~300 B olduğunda bu kurulum maliyeti toplam gecikmenin önemli bir bölümünü oluşturuyor.

Multiplexing bu kurulumu tamamen ortadan kaldırıyor: tek bağlantı bir kez kurulup 50 stream süresiz taşıyor.

**5 bağlantı neden 1 bağlantıdan farklı değil?** Darboğaz bağlantı sayısı değil, 2 vCPU'nun Netty worker thread kapasitesi. Sunucu bağlantı başına ek bir şey yapmıyor — 50 stream hangi bağlantıdan gelirse gelsin aynı thread pool'da işleniyor. Bağlantı sayısını artırmak CPU'yu artırmıyor.

---

### Bulgu 2 (G2) — Büyük payload: multiplexing throughput'u kurtaramıyor, ama p95'i düzeltiyor

`ListProducts` (~20MB yanıt), 10 eş zamanlı istemciyle:

| Model | `--conn` | `--conc` | Ortalama | p95 | RPS |
|---|---|---|---|---|---|
| k6 modeli | 10 | 10 | 1728ms | 2906ms | 6 |
| Gerçek multiplexing | 1 | 10 | 1639ms | **1950ms** | 6 |
| Üretim havuzu | 3 | 10 | 1566ms | 2513ms | 6 |

Üç modelde de throughput aynı: saniyede 6 istek. Bağlantı sayısı ne olursa olsun fark etmiyor.

**Neden?** Bu senaryoda darboğaz ağ veya bağlantı değil, CPU serializasyon maliyeti. Spring Boot her `ListProducts` çağrısında 5000 ürünü Protobuf'a serileştiriyor; bu işlem `MessageFramer.writeKnownLengthUncompressed` kanalıyla tüm 20MB'ı tek seferde Netty direct buffer'a yazıyor. 10 eş zamanlı çağrı = 10 × 20MB = 200MB direct buffer + 10 serializasyon işi, bunlar CPU çekirdeklerine dağıtılıyor. Multiplexing bu CPU işini dağıtamaz.

**Multiplexing yine de p95'te öne çıkıyor:** 10 ayrı bağlantıda yük Netty thread pool'una rastgele dağıtılıyor — bazı bağlantılar az iş alırken bazıları uzun kuyrukta bekliyor, bu asimetri p95'i şişiriyor (2906ms). Tek bağlantıdaki 10 stream aynı thread pool'u düzenli paylaşıyor; herkes benzer süre bekliyor ve p95 **%33 iyileşiyor** (1950ms).

**Asıl mimari sorun:** 20MB'lık veriyi Unary gRPC ile göndermek yanlış araç seçimi. Sunucu yanıtı iletmeden önce tüm 20MB'ı single direct buffer'a sığdırmak zorunda; bu hem GC baskısı hem OOM riski yaratıyor (test sırasında bizzat yaşandı: `OutOfMemoryError: Cannot reserve direct buffer memory`). Doğru çözüm Server Streaming RPC — veri chunk'lar halinde akıtılır, ne bellek baskısı oluşur ne de istemci ilk byte'ı beklemek zorunda kalır.

---

### Bulgu 3 (G5) — Head-of-Line Blocking: tam blok yok, ama kaynak çakışması ölçülebilir

Arka planda 2 sürekli `ListProducts` akışı çalışırken `GetProduct` latency'si:

| Durum | Test Konfigürasyonu | p95 | RPS |
|---|---|---|---|
| Baseline (yük yok) | G1 multiplexing: 1 conn, 50 conc | 20.9ms | 4267 |
| 20MB arka plan baskısı | G5: bg 1 conn × 2 ListProducts + fg 1 conn × 50 GetProduct | **28.5ms** | 3286 |

`GetProduct` p95'i **%36 arttı**, throughput **%23 düştü**.

**HTTP/2 stream izolasyonu neden tam çalışmadı?** HTTP/2 stream'leri birbirini uygulama katmanında bloklamaz — her stream bağımsız akış kontrolüne sahip. Ancak TCP katmanı ortak:

1. **TCP congestion window**: Sunucu 2 × 20MB akıtırken TCP gönderme penceresi doluyor; OS kernel tüm veriler için aynı TCP send buffer'ı kullanıyor — `GetProduct` frame'leri 20MB frame'lerin arkasına kuyruk giriyor.
2. **Netty write backpressure**: Outbound buffer dolarsa Netty `channel.isWritable()` false döndürüyor ve `GetProduct` yanıtları yazma kuyruğuna giriyor.
3. **CPU paylaşımı**: 2 × 20MB serializasyon 2 vCPU'nun büyük bölümünü tüketiyor; `GetProduct` işleyicileri için kalan zaman dilimi daralıyor.

**Tam blok yok** — hata oranı sıfır, sadece ölçülebilir gecikme artışı. HTTP/2 frame interleaving mekanizması `GetProduct` yanıtlarını 20MB akışları arasına serpiştiriyor; mutlak bir kuyruk oluşmuyor. Ancak paylaşılan fiziksel kaynaklar (TCP buffer, CPU) izolasyonu zayıflatıyor.

**Üretim çözümü:** Büyük payload çağrıları (ListProducts) ile küçük, gecikme-hassas çağrıları (GetProduct) ayrı gRPC `ManagedChannel` havuzlarında tutmak bu etkiyi tamamen ortadan kaldırır.

---

### Bulgu 4 (G4) — Sabit havuz boyutunda gRPC, HTTP/1.1'den %31 daha fazla iş üretiyor

Bir upstream servisin 10 kalıcı bağlantıyla downstream'e `GetProduct` çağrısı yaptığı senaryo (k6 S9 eşdeğeri):

| Model | `--conn` | `--conc` | Max eş zamanlı istek | RPS |
|---|---|---|---|---|
| HTTP/1.1 modeli (k6 S9) | 10 | 10 | 10 | 3341 |
| gRPC pool (multiplexing) | 10 | 100 | 100 | **4368** |
| gRPC tek bağlantı (üst sınır) | 1 | 100 | 100 | 4737 |

HTTP/1.1'de 10 bağlantı aynı anda yalnızca 10 istek taşıyabilir — bir istemci cevap gelene kadar o bağlantıyı bloklıyor (HTTP/1.1 pipelining teoride var ama pratikte yaygın kullanılmıyor). gRPC'de aynı 10 bağlantı her biri 10 stream taşıyarak 100 eş zamanlı isteği kaldırıyor ve aynı havuzdan **%31 daha fazla RPS** üretiyor.

Tek bağlantı (4737 RPS) ile 10 bağlantılı havuz (4368 RPS) arasındaki %8 fark, 10 ayrı bağlantının küçük ek yükünden kaynaklanıyor (her bağlantının kendi flow-control state'i var). Üretimde 3-5 bağlantı genellikle yeterlidir; bağlantı sayısını artırmak yerine her bağlantıdaki concurrency'yi artırmak daha verimli.

---

### Bulgu 5 (G3) — Stres altında multiplexing ortalamayı yavaşlatıyor, p95'i stabilize ediyor

30 eş zamanlı `ListProducts` (~20MB), stres senaryosu:

| Model | `--conn` | `--conc` | Ortalama | p95 |
|---|---|---|---|---|
| k6 modeli | 30 | 30 | 4838ms | 9670ms |
| Gerçek multiplexing | 1 | 30 | 5806ms | **8675ms** |

Multiplexing ortalamada **%20 daha yavaş**, p95'te **%10 daha iyi**.

**Ortalama neden daha yavaş?** 30 ayrı bağlantıda bazı bağlantılar hızlı Netty worker thread'lerine denk geliyor ve erkenden tamamlanıyor; bu düşük değerler ortalamayı aşağı çekiyor. Tek bağlantıda 30 stream sırasını bekliyor — erken biten yok, herkes adil miktarda bekliyor.

**p95 neden daha iyi?** Ayrı bağlantı modelinde geç kalan bağlantılar çok uzun kuyrukta bekleyebiliyor (thread starvation). Tek bağlantıda tüm stream'ler aynı kuyruğu eşit paylaşıyor: kimse çok ileri gitmiyor, kimse çok uzun kalmıyor. Bu fair-queuing davranışı tail latency'yi düzleştiriyor.

**Stres altında hangi metrik önemli?** Ortalama değil, p95. Birkaç isteğin çok hızlı olması sistemin iyi gittiği anlamına gelmez; %5'lik kesimin 9.6 saniye beklemesi SLA ihlalidir. Multiplexing bu perspektiften stres altında daha güvenilir.

---

### Sonuç: Multiplexing ne zaman kullanılır?

| Durum | Öneri | Gerekçe |
|---|---|---|
| Küçük, sık çağrılar (GetProduct) | Multiplexing — `--connections 1-5 --concurrency N` | Bağlantı kurma maliyeti dominant, multiplexing 2× RPS |
| Büyük Unary payload (20MB+) | **Önce Server Streaming RPC**; multiplexing ikincil | Darboğaz CPU serializasyonu, bağlantı modeli fark etmiyor |
| Sabit connection pool (microservice havuzu) | gRPC pool — az bağlantı, yüksek concurrency | Aynı havuzdan %31 daha fazla iş |
| Karışık büyük+küçük çağrı | Ayrı channel havuzları | Büyük akışlar küçük istekleri geciktiriyor (G5) |
| Stres altında tail latency | Multiplexing | p95 daha dengeli dağılım |
| Bağlantı havuzu boyutu seçimi | 3-5 bağlantı yeterli | 5 bağlantı = 1 bağlantı (G1); sayıyı değil concurrency'yi artır |

### Çalıştırma 4 — beklemede · S9/S10 + GAP2=120s + S8 maxVUs=50

**Değişiklikler:**

| Değişiklik | Öncesi | Sonrası |
|---|---|---|
| `grpc_s4_warm` (S4b) | Var | **Kaldırıldı** |
| S4 sonrası recovery | GAP_W=30s + S4b(55s) = 85s (S4b hata veriyordu) | **GAP2=120s** (temiz bekleme) |
| S8 maxVUs | 300 | **50** |
| S9/S10 | Yok | **Eklendi** (10 VU pool, 200 RPS) |
| Network ölçümü | Cumulative counter (karşılaştırılamaz) | **Per-request Trend, sadece S2** |

**Beklenen iyileşmeler:**
- S8 gRPC: Temiz sunucuda çalışır → düşük hata oranı
- S9/S10: Pool simülasyonu — gRPC multiplexing farkı görünür
- Network bölümü: Anlamlı MB/req karşılaştırması

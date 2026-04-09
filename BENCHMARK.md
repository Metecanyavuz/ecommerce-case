# gRPC vs REST Benchmark — Senaryo Rehberi

Bu döküman, `benchmark/` klasöründeki k6 testlerinin ne yaptığını, hangi koşulları simüle ettiğini ve her senaryodan beklenen sonuçları açıklar.

---

## Mimari

```
k6-runner (benchmark servis)
    │
    ├── REST  → HTTP/1.1 + JSON   → product-service:8080
    └── gRPC  → HTTP/2 + Protobuf → product-service:9090
                                         │
                                    PostgreSQL (products schema)
                                    5000 ürün, ~3.9 KB description
```

Tüm trafik Railway internal network üzerinden akar (`*.railway.internal`). Proxy katmanı yok — iki protokol aynı ağ yolunu kullanır, karşılaştırma adildir.

---

## Bağımsız ve Bağımlı Değişkenler

### Bağımsız Değişkenler *(bizim kontrol ettiğimiz)*
| Değişken | Değerler |
|----------|---------|
| Protokol | REST (HTTP/1.1 + JSON) / gRPC (HTTP/2 + Protobuf) |
| Payload boyutu | ~300 bytes (1 ürün) / ~20 MB (5000 ürün) |
| Eşzamanlı kullanıcı | 50 VU / 200 VU / 0→500-5000 VU (spike) |

### Bağımlı Değişkenler *(ölçtüğümüz)*
| Metrik | Açıklama |
|--------|----------|
| `avg` | Ortalama istek süresi — tek başına yanıltıcı olabilir |
| `p(95)` | İsteklerin %95'i bu süreden kısa — SLA tanımında kullanılan standart metrik |
| `max` | En yavaş istek — spike ve outlier davranışını gösterir |
| Throughput (req/s) | Sistemin kapasite sınırı |
| `data_received` | Gerçek bandwidth tüketimi |
| Hata oranı | Sistemin dayanıklılığı, özellikle S4'te kritik |

### Karıştırıcı Değişkenler *(göz önünde bulundurulmalı)*
- **JVM warm-up:** S1 soğuk JVM'de çalışır, sonraki senaryolar JIT avantajından yararlanır
- **Test sırası:** Senaryolar sıralı çalıştığından sonrakiler DB connection pool'u hazır bulur
- **Altyapı değişkenliği:** Railway/AWS kaynak tahsisi sabit olmayabilir
- **Tek çalıştırma:** İstatistiksel güvenilirlik için 3+ tekrar ve ortalama alınması gerekir

---

## Senaryolar

### S1 — Küçük Payload, Normal Yük

| | |
|--|--|
| **Endpoint** | `GET /products/1` / `GetProduct(id=1)` |
| **Payload** | ~300 bytes (1 ürün) |
| **VU** | 50 |
| **Süre** | 110 saniye (10s warm-up → 30s ramp → 60s sustained → 10s down) |

**Ne simüle eder:**
Kullanıcının ürün detay sayfasını açması. Order Service'in sipariş doğrulamak için tek ürün sorgusu atması. Günlük normal trafik.

**Neyi ölçer:**
Payload küçük olduğundan serialization maliyeti sıfıra yakın. Görünen fark tamamen **saf protokol overhead'i** — HTTP/1.1 vs HTTP/2 header boyutu, bağlantı maliyeti.

**Beklenen sonuç:**
İki protokol çok yakın çıkar. REST hafif avantajlı olabilir — Spring MVC'nin Tomcat pipeline'ı küçük senkron istekler için optimize edilmiş.

---

### S2 — Büyük Payload, Normal Yük

| | |
|--|--|
| **Endpoint** | `GET /products` / `ListProducts()` |
| **Payload** | ~20 MB (5000 ürün, ~3.9 KB description) |
| **VU** | 50 |
| **Süre** | 110 saniye |

**Ne simüle eder:**
Admin panelinin tüm ürün katalogunu yüklemesi. Backend servisler arası toplu veri transferi. Arama motorunun ürünleri indekslemesi.

**Neyi ölçer:**
Asıl **serialization savaşı** burada yaşanır. Spring Boot 5000 ürünü JSON'a çevirirken binlerce `String` nesnesi yaratır, GC baskısı başlar. gRPC aynı datayı binary Protobuf ile ~%40 daha küçük boyutta, çok daha az CPU harcayarak gönderir. **Bandwidth farkı** da burada gözlemlenir.

**Beklenen sonuç:**
gRPC belirgin öne çıkar. Hem latency düşer (daha az veri gönderilir) hem throughput artar (CPU daha az yanar).

---

### S3 — Büyük Payload, Yüksek Eşzamanlılık

| | |
|--|--|
| **Endpoint** | `GET /products` / `ListProducts()` |
| **Payload** | ~20 MB (5000 ürün) |
| **VU** | 200 |
| **Süre** | 70 saniye (10s → ramp 200 → 30s sustained → 10s down) |

**Ne simüle eder:**
S2'nin aynı senaryosu ama aynı anda 200 kullanıcı istek atıyor. Mesai başlangıcında tüm operasyon ekibinin admin paneli açması. Yoğun ama stabil trafik.

**Neyi ölçer:**
S2'deki serialization farkının yüksek concurrency altında nasıl **büyüdüğünü** ölçer. HTTP/2 multiplexing burada devreye girer — gRPC tek TCP bağlantısı üzerinden 200 isteği paralel stream olarak işleyebilir. REST her bağlantı için ayrı kaynak tüketir.

**Beklenen sonuç:**
S2'ye kıyasla iki protokol arasındaki makas açılır. gRPC'nin p(95) ve max latency avantajı daha belirgin hale gelir.

---

### S4 — Flash Sale Spike

| | |
|--|--|
| **Endpoint** | `GET /products/1` / `GetProduct(id=1)` |
| **Payload** | ~300 bytes (1 ürün) |
| **VU** | 0 → `MAX_SPIKE_VU` (Railway: **500**, AWS: **5000**), 10 saniyede |
| **Süre** | 50 saniye (10s spike → 30s sustained → 10s down) |

**Ne simüle eder:**
Black Friday açılışı. İndirim kampanyasının başladığı an. Sistem saniyeler içinde normal kapasitesinin çok üzerine çıkar.

Küçük payload (GetProduct) kullanılmasının sebebi: serialization'ı denklemden çıkarmak. Sadece **ani bağlantı baskısı** altında protokollerin bağlantı yönetimini izole etmek.

**Neyi ölçer:**
**Thread pool tükenmesi ve bağlantı yönetimi farkı.**

- **REST (HTTP/1.1):** Tomcat default thread pool = **200 thread**. 500+ VU gelince thread'ler dolar, yeni istekler kuyrukta bekler, kuyruk taşarsa `Connection Refused` / timeout hataları başlar.
- **gRPC (HTTP/2 + Netty):** Non-blocking I/O — thread sayısı VU sayısıyla orantılı değil. Tek TCP bağlantısı üzerinde multiplexing ile binlerce isteği işler. Spike altında hata oranı düşük kalır.

**Beklenen sonuç:**
En dramatik farkın görüleceği senaryo. REST'te hata oranı ve max latency spike atarken gRPC sakin kalır.

---

## Senaryo Özet Tablosu

| | S1 | S2 | S3 | S4 |
|--|:--:|:--:|:--:|:--:|
| Endpoint | GetProduct | ListProducts | ListProducts | GetProduct |
| Payload | ~300 B | ~20 MB | ~20 MB | ~300 B |
| Max VU | 50 | 50 | 200 | 500 / 5000 |
| Süre | 110s | 110s | 70s | 50s |
| Ana ölçüm | Protokol overhead | Serialization | Concurrency + Serialization | Bağlantı yönetimi |
| gRPC avantajı | Minimal | Belirgin | Belirgin | Çok belirgin |

---

## Çalıştırma

### Railway

```bash
# k6-runner servisinde otomatik çalışır
# Env variables (Railway Dashboard → Variables):
REST_URL=http://product-service.railway.internal:8080
GRPC_URL=product-service.railway.internal:9090
MAX_SPIKE_VU=500
```

### AWS

```bash
REST_URL=http://<product-service-internal-ip>:8080 \
GRPC_URL=<product-service-internal-ip>:9090 \
MAX_SPIKE_VU=5000 \
k6 run benchmark/benchmark.js
```

**Önerilen AWS instance boyutları:**

| Servis | Instance | Neden |
|--------|----------|-------|
| k6-runner | `c5.2xlarge` (8 vCPU, 16 GB) | 5000 VU ≈ 5 GB RAM gerektirir |
| product-service | `t3.large` (2 vCPU, 8 GB) | Tomcat 200 thread dolar — görmek istediğin bu |
| PostgreSQL | `db.t3.medium` | HikariCP 10 conn bottleneck görünür |

---

## Rapor Formatı

Benchmark tamamlandığında k6 otomatik olarak aşağıdaki formatı stdout'a basar:

```
╔══════════════════════════════════════════════════════════════════╗
║          BENCHMARK KARŞILAŞTIRMA RAPORU — REST vs gRPC           ║
╚══════════════════════════════════════════════════════════════════╝

  ┌─ S1 — Küçük Payload (GetProduct, 1 ürün, 50 VU)
  │  avg    REST: 5.2ms      gRPC: 6.1ms      → REST %17 daha hızlı
  │  p(95)  REST: 9.0ms      gRPC: 10.5ms     → REST %17 daha hızlı
  └─ max    REST: 45.0ms     gRPC: 38.0ms     → gRPC %16 daha hızlı

  ┌─ S2 — Büyük Payload (ListProducts, ~20MB, 50 VU)
  ...

  GENEL ORTALAMA p(95)  [4 senaryonun ortalaması]
    REST : xx.xms
    gRPC : xx.xms
    Sonuç: ...
```

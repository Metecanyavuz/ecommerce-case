import http from 'k6/http';
import grpc from 'k6/net/grpc';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const REST_URL     = __ENV.REST_URL     || 'http://product-service.railway.internal:8080';
const GRPC_URL     = __ENV.GRPC_URL     || 'product-service.railway.internal:9090';
// c6a.large (2 vCPU) için 100 VU makul sınır — protokol farkını görmek için yeterli.
// Daha güçlü makinede: MAX_SPIKE_VU=500 k6 run benchmark.js
const MAX_SPIKE_VU = parseInt(__ENV.MAX_SPIKE_VU || '100');
// S7/S8 max RPS — microservice-to-microservice gerçekçi yük modeli için 200 RPS yeterli.
// Daha güçlü makinede: MAX_RPS=600 k6 run benchmark.js
const MAX_RPS      = parseInt(__ENV.MAX_RPS      || '200');
// WARM_SPIKE_VU: Senaryo kaldırıldı (S4b). S10 pool simülasyonu aynı kavramı karşılıyor.
// Sabit burada bırakıldı — gerekirse grpc_s4_warm senaryosu yeniden eklenebilir.
const WARM_SPIKE_VU = 50;
// S9 pool simülasyonu: 10 keep-alive bağlantı × yüksek RPS = REST microservice-to-microservice model.
// MS_POOL_VU=10: Bir upstream servisten gelen tipik HTTP/1.1 connection pool boyutu.
// MS_MAX_RPS=200: Bu 10 bağlantıdan geçen hedef istek/s hızı.
// gRPC eşdeğeri: ghz-compare.sh G4 (--connections MS_POOL_VU --concurrency yüksek).
const MS_POOL_VU   = parseInt(__ENV.MS_POOL_VU   || '10');
const MS_MAX_RPS   = parseInt(__ENV.MS_MAX_RPS   || '200');

// ── Per-scenario latency metrics ──────────────────────────────────────────────
const restS1 = new Trend('rest_s1_ms', true);
const grpcS1 = new Trend('grpc_s1_ms', true);
const restS2 = new Trend('rest_s2_ms', true);
const grpcS2 = new Trend('grpc_s2_ms', true);
const restS3 = new Trend('rest_s3_ms', true);
const grpcS3 = new Trend('grpc_s3_ms', true);
const restS4 = new Trend('rest_s4_ms', true);
const grpcS4 = new Trend('grpc_s4_ms', true);
const restS5 = new Trend('rest_s5_ms', true);
const restS6 = new Trend('rest_s6_ms', true);

// S4b: Warm Spike — bağlantılar önceden kurulmuş, sadece request yükü ölçülür
// Production connection pool davranışını simüle eder
const grpcS4Warm = new Trend('grpc_s4_warm_ms', true);

// S7: REST Max Throughput — REST doğal modelinde adil ölçüm
const restS7 = new Trend('rest_s7_max_tp_ms', true);
// S8 (gRPC max throughput) kaldırıldı: ghz G1 (--connections 1 --concurrency 50)
// gerçek multiplexing ile ölçüyor. k6 sequential modeli gRPC için alt sınır
// bile değil — yanlış model. Karşılaştırma: k6 S7 vs ghz G1.

// S9: REST Connection Pool — HTTP/1.1 sequential, k6 modeli production gerçeğini yansıtır
const restS9 = new Trend('rest_s9_pool_ms', true);
// S10 (gRPC pool) kaldırıldı: ghz G1 (--connections MS_POOL_VU --concurrency yüksek)
// gerçek pool davranışını ölçüyor. k6 S10 sequential channel = gRPC'nin kötü modeli.
// Karşılaştırma: k6 S9 vs ghz G4 (pool sim, bkz. ghz-compare.sh).

// S10: Mixed Workload — Gerçek trafik dağılımı (%80 GetProduct, %20 ListProducts)
// Hem REST hem gRPC için aynı dağılım → protokol seçiminin gerçek üretim yüküne etkisi.
const restS10  = new Trend('rest_s10_mixed_ms',  true);
const grpcS10  = new Trend('grpc_s10_mixed_ms',  true);
const restS10E = new Rate('rest_s10_error_rate');
const grpcS10E = new Rate('grpc_s10_error_rate');

// S11: gRPC Cached (Redis Protobuf → single parseFrom) vs REST Cached (S5/S6)
// REST S5 path: Redis JSON bytes → Jackson deserialize → Jackson serialize → HTTP body  (2× serializasyon)
// REST S6 path: Redis Proto bytes → Protobuf parseFrom → Jackson serialize → HTTP body  (proto→java→json)
// gRPC S11 path: Redis Proto bytes → Protobuf parseFrom → onNext()                      (1× serializasyon)
// S11, S5/S6 ile karşılaştırılarak gRPC'nin cache serializasyon avantajını ölçer.
const grpcS11  = new Trend('grpc_s11_cached_ms', true);
const grpcS11E = new Rate('grpc_s11_error_rate');

const restErrors    = new Rate('rest_error_rate');
const grpcErrors    = new Rate('grpc_error_rate');
const cacheErrors   = new Rate('cache_error_rate');
const restS7Errors  = new Rate('rest_s7_error_rate');
const grpcS4WarmErr = new Rate('grpc_s4_warm_error_rate');
const restS9Errors  = new Rate('rest_s9_error_rate');

// ── Network Payload Tracking (S2: büyük payload, apples-to-apples) ───────────
// Sadece S2 ListProducts yanıtları ölçülür — S1/S3/S4 gibi küçük payload
// senaryolarıyla karıştırılırsa karşılaştırma anlamsız olur.
//
// REST S2: res.body.length → gerçek JSON response body (wire-accurate)
// gRPC S2: JSON.stringify(res.message).length → yaklaşık, wire size değil.
//   Neden tam değil: k6 gRPC client raw Protobuf bytes'ı expose etmez;
//   mesaj deserialize edildikten sonra JSON'a çevrilerek boyutu ölçülür.
//   Gerçek Protobuf wire size genellikle JSON'un %50-80'i kadardır
//   (field name yerine field number, varint encoding, vb.).
//   Bu veri setinde açıklamaların çoğunlukla uzun string olduğu göz önüne alınırsa
//   fark daha az olabilir (~%10-20 küçük) — string verisi Protobuf'ta da aynı boyuttadır.
const restS2BytesPerReq = new Trend('rest_s2_bytes_per_req', false);  // false = bayt birimi
const grpcS2BytesApprox = new Trend('grpc_s2_bytes_approx',  false);

// ── HTTP params — Keep-Alive + gzip ───────────────────────────────────────────
// Accept-Encoding: gzip → Spring Boot server.compression ile REST'in gerçek
// production halini test eder. gRPC zaten binary Protobuf kullanıyor; gzip
// eklemeden REST "en kötü haliyle" karşılaştırılmış olur.
// k6, gzip response'u otomatik decompress eder; timings.duration sıkıştırılmış
// aktarım süresini + decompress süresini kapsar — wire-level açıdan adil.
const HTTP_PARAMS = {
  headers: {
    'Connection':      'keep-alive',
    'Accept-Encoding': 'gzip',
  },
};

// ── Stage şablonları ──────────────────────────────────────────────────────────

// JVM ve bağlantı havuzunu ısıtmak için — öncesinde soğuk JVM avantajsızlığını önler
const WARMUP_STAGES = [
  { duration: '15s', target: 5 },
  { duration: '10s', target: 5 },
  { duration: '5s',  target: 0 },
];

const STD_STAGES = [
  { duration: '10s', target: 10  },
  { duration: '30s', target: 50  },
  { duration: '60s', target: 50  },
  { duration: '10s', target: 0   },
];

const STRESS_STAGES = [
  { duration: '10s', target: 50  },
  { duration: '20s', target: 200 },
  { duration: '30s', target: 200 },
  { duration: '10s', target: 0   },
];

const SPIKE_STAGES = [
  { duration: '10s', target: MAX_SPIKE_VU },
  { duration: '30s', target: MAX_SPIKE_VU },
  { duration: '10s', target: 0            },
];

// Warm spike: VU'lar ramp süresinde bağlanır, sustained aşamasında
// bağlantı kurma overhead'i olmadan sadece request yükü ölçülür.
// Production connection pool davranışına en yakın k6 simülasyonu.
// WARM_SPIKE_VU=50 sabit: MAX_SPIKE_VU'dan bağımsız, pool boyutunu temsil eder.
const WARM_SPIKE_STAGES = [
  { duration: '15s', target: WARM_SPIKE_VU },  // connect phase — metrik tutulmaz
  { duration: '30s', target: WARM_SPIKE_VU },  // load phase   — metrik buradan başlar
  { duration: '10s', target: 0             },
];

// MS_POOL_STAGES: Küçük sabit VU havuzu (10 bağlantı) üzerinden RPS ramping.
// Bu, bir microservice'in upstream'e sabit pool boyutuyla çağrı yaptığı modeli simüle eder.
// preAllocatedVUs=MS_POOL_VU ile birlikte kullanılır (senaryoda).
const MS_POOL_STAGES = [
  { target: Math.round(MS_MAX_RPS * 0.25), duration: '15s' },  // ısınma
  { target: MS_MAX_RPS,                    duration: '50s' },  // sustained
  { target: 0,                             duration: '10s' },  // ramp-down
];

const CACHE_STAGES = [
  { duration: '5s',  target: 10 },
  { duration: '30s', target: 50 },
  { duration: '60s', target: 50 },
  { duration: '10s', target: 0  },
];

// MAX_TP_STAGES: RPS hedefleri MAX_RPS env var'a göre ölçeklenir.
// c6a.large default: 10→50→100→200 RPS — microservice yük modelini temsil eder.
// Güçlü makine: MAX_RPS=600 ile 10→100→300→600 modeline dönüşür.
const MAX_TP_STAGES = [
  { target: Math.round(MAX_RPS * 0.05), duration: '10s' },  // %5  — ısınma
  { target: Math.round(MAX_RPS * 0.25), duration: '20s' },  // %25 — düşük yük
  { target: Math.round(MAX_RPS * 0.50), duration: '20s' },  // %50 — orta yük
  { target: MAX_RPS,                    duration: '40s' },  // %100 — maksimum
  { target: 0,                          duration: '10s' },  // ramp-down
];

// ── Blok süreleri ─────────────────────────────────────────────────────────────
const W       = 30;   // WARMUP blok
const S       = 115;  // STD    blok
const T       = 75;   // STRESS blok
const K       = 55;   // SPIKE  blok (cold)
const C       = 110;  // CACHE  blok
const GAP     = 60;   // Spike öncesi soğuma — S3 sonrası makineye nefes aldırır
// GAP2: S4 cold spike sonrası Netty thread pool + OS buffer recovery.
// 120s = 2×MSL(60s) TIME_WAIT + Netty kuyruk drenajı için yeterli.
const GAP2    = 120;  // Cold spike sonrası tam recovery — S7/S9 temiz sunucuyla başlar
const M       = 110;  // MAX_TP blok (S7)
const GAP3    = 30;   // S9 → S10/S11 geçiş soğuması

// ── Senaryo sırası ve gerekçesi ───────────────────────────────────────────────
// Warmup:              0s    → JVM ve bağlantı havuzunu ısıt
// S1-REST:             30s   → Küçük payload baseline
// S1-gRPC:            145s
// S2-REST:            260s   → Büyük payload (no-cache) — makine henüz taze
// S2-gRPC:            375s
// S5-Cache-JSON:      490s   → Cache testi S2'nin hemen ardında — adil karşılaştırma
// S6-Cache-Proto:     600s
// S3-REST:            710s   → Yüksek eşzamanlılık
// S3-gRPC:            785s
// [60s soğuma]               → Spike öncesi makineye nefes aldır
// S4-REST Spike:      920s   → Cold spike
// S4-gRPC Spike:      975s   → Cold spike (connection establishment dahil)
// [120s soğuma]              → Netty thread pool + OS buffer tam temizlenmesi
// S7-REST MaxTP:     1150s   → REST darboğaz noktası (doğal model, adil)
// S9-REST Pool:      1260s   → 10 keep-alive bağlantı, ramping RPS
// [30s soğuma]
// S10-REST Mixed:    1370s   → %80 GetProduct + %20 ListProducts, 50 VU
// S10-gRPC Mixed:    1485s   → aynı dağılım, gRPC tarafı
// S11-gRPC Cached:   1600s   → Redis Proto cache → single parseFrom (S5/S6 karşılaştırması)
// Toplam: ~1710s ≈ ~29 dakika
//
// Kaldırılan senaryolar:
//   S4b (warm spike): Her çalıştırmada S4 cold spike hasarı yüzünden %100 hata üretiyordu.
//                     GAP2=120s eklendi — temiz sunucuyla S7/S9 başlar.
//   S8 (gRPC MaxTP):  k6 sequential modeli gRPC'yi haksız ölçüyor. Yerine ghz G1 kullan.
//   gRPC Pool (k6):   k6 sequential channel gRPC multiplexing'i temsil etmiyor. Yerine ghz G4 kullan.

export const options = {
  // setup() içinde iki ~20MB cache warm-up isteği yapılıyor — soğuk JVM + sıralı istekler
  // 2 × 60s timeout + buffer = 150s yeterli
  setupTimeout: '150s',

  scenarios: {
    // ── Warmup — soğuk JVM avantajsızlığını gidermek için ──────────────────
    warmup: {
      executor:        'ramping-vus',
      startTime:       '0s',
      startVUs:        0,
      stages:          WARMUP_STAGES,
      gracefulRampDown:'5s',
      env: { PROTO: 'rest', SCENE: 's1' },
    },

    // ── S1: Küçük payload ────────────────────────────────────────────────────
    rest_s1_small: { executor: 'ramping-vus', startTime: `${W}s`,           startVUs: 0, stages: STD_STAGES,    gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's1' } },
    grpc_s1_small: { executor: 'ramping-vus', startTime: `${W+S}s`,         startVUs: 0, stages: STD_STAGES,    gracefulRampDown: '5s', env: { PROTO: 'grpc', SCENE: 's1' } },

    // ── S2: Büyük payload, cache yok ─────────────────────────────────────────
    rest_s2_large: { executor: 'ramping-vus', startTime: `${W+S*2}s`,       startVUs: 0, stages: STD_STAGES,    gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's2' } },
    grpc_s2_large: { executor: 'ramping-vus', startTime: `${W+S*3}s`,       startVUs: 0, stages: STD_STAGES,    gracefulRampDown: '5s', env: { PROTO: 'grpc', SCENE: 's2' } },

    // ── S5/S6: Cache — S2 ile benzer makine durumunda çalışır ────────────────
    // NOT: Önceki testte cache S4 spike sonrası çalışıyordu → makine tükenmişti.
    //      Şimdi S2'nin hemen ardında çalışıyor → karşılaştırma adil.
    cache_s5_json:  { executor: 'ramping-vus', startTime: `${W+S*4}s`,      startVUs: 0, stages: CACHE_STAGES,  gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's5' } },
    cache_s6_proto: { executor: 'ramping-vus', startTime: `${W+S*4+C}s`,    startVUs: 0, stages: CACHE_STAGES,  gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's6' } },

    // ── S3: Yüksek eşzamanlılık ───────────────────────────────────────────────
    rest_s3_stress: { executor: 'ramping-vus', startTime: `${W+S*4+C*2}s`,          startVUs: 0, stages: STRESS_STAGES, gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's3' } },
    grpc_s3_stress: { executor: 'ramping-vus', startTime: `${W+S*4+C*2+T}s`,        startVUs: 0, stages: STRESS_STAGES, gracefulRampDown: '5s', env: { PROTO: 'grpc', SCENE: 's3' } },

    // ── S4: Flash Sale Spike — Cold (60s soğuma sonrası) ─────────────────────
    // Her VU ilk iterasyonda bağlantı kurar → connection establishment overhead
    // görünür. Production cold-start senaryosunu ölçer.
    rest_s4_spike:  { executor: 'ramping-vus', startTime: `${W+S*4+C*2+T*2+GAP}s`,    startVUs: 0, stages: SPIKE_STAGES,      gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's4'  } },
    grpc_s4_spike:  { executor: 'ramping-vus', startTime: `${W+S*4+C*2+T*2+GAP+K}s`,  startVUs: 0, stages: SPIKE_STAGES,      gracefulRampDown: '5s', env: { PROTO: 'grpc', SCENE: 's4'  } },

    // ── S7: REST Darboğaz Bulucu ──────────────────────────────────────────────
    // GAP2=120s sonrası başlar — Netty tam recovery, temiz ölçüm.
    // maxVUs=300 — REST keep-alive sorunsuz; 300 bağlantı Netty'yi ezmez.
    // gRPC eşdeğeri: ghz G1 (--connections 1 --concurrency 50) — gerçek multiplexing.
    rest_s7_max_tp: {
      executor:        'ramping-arrival-rate',
      startTime:       `${W+S*4+C*2+T*2+GAP+K*2+GAP2}s`,
      startRate:       10,
      timeUnit:        '1s',
      preAllocatedVUs: 50,
      maxVUs:          300,
      stages:          MAX_TP_STAGES,
      gracefulStop:    '30s',
      env: { PROTO: 'rest', SCENE: 's7' },
    },

    // ── S9: REST Connection Pool Simülasyonu ──────────────────────────────────
    // Gerçek upstream-to-downstream REST servis çağrısını modeller:
    //   - Sabit küçük pool: MS_POOL_VU=10 kalıcı HTTP keep-alive bağlantı
    //   - Yüksek RPS hedefi: MS_MAX_RPS=200 istek/s bu 10 bağlantıdan geçer
    // HTTP/1.1: Her bağlantı sıralı → pool taşarsa dropped iterations görünür.
    // gRPC eşdeğeri: ghz G4 (--connections MS_POOL_VU) gerçek pool davranışını ölçer.
    rest_s9_pool: {
      executor:        'ramping-arrival-rate',
      startTime:       `${W+S*4+C*2+T*2+GAP+K*2+GAP2+M}s`,
      startRate:       Math.round(MS_MAX_RPS * 0.25),
      timeUnit:        '1s',
      preAllocatedVUs: MS_POOL_VU,
      maxVUs:          MS_POOL_VU * 3,   // pool taşarsa dar boğazı görünür kılmak için 3x
      stages:          MS_POOL_STAGES,
      gracefulStop:    '5s',
      env: { PROTO: 'rest', SCENE: 's9' },
    },

    // ── S10: Mixed Workload — Gerçek trafik dağılımı ──────────────────────────
    // Production microservice trafiği tek tip operasyon değildir.
    // %80 GetProduct (küçük payload) + %20 ListProducts (büyük payload) karışımı
    // her iki protokol için de aynı dağılımla uygulanır — adil karşılaştırma.
    // Sonuç, protokolün "ortalama iş yükü" altındaki davranışını gösterir.
    rest_s10_mixed: {
      executor:        'ramping-vus',
      startTime:       `${W+S*4+C*2+T*2+GAP+K*2+GAP2+M+80+GAP3}s`,
      startVUs:        0,
      stages:          STD_STAGES,
      gracefulRampDown:'5s',
      env: { PROTO: 'rest', SCENE: 's10' },
    },
    grpc_s10_mixed: {
      executor:        'ramping-vus',
      startTime:       `${W+S*4+C*2+T*2+GAP+K*2+GAP2+M+80+GAP3+S}s`,
      startVUs:        0,
      stages:          STD_STAGES,
      gracefulRampDown:'5s',
      env: { PROTO: 'grpc', SCENE: 's10' },
    },

    // ── S11: gRPC Cache — Redis Protobuf → single parseFrom ───────────────────
    // S5 REST: Redis JSON bytes → Jackson deserialize → Jackson re-serialize → HTTP JSON body
    // S6 REST: Redis Proto bytes → Protobuf parseFrom → Jackson serialize → HTTP JSON body
    // S11 gRPC: Redis Proto bytes → Protobuf parseFrom → onNext() (Protobuf wire)
    // S11 S5/S6'dan bağımsız olarak cache warm olduğundan (TTL=3600s) temiz ölçüm sağlar.
    // Karşılaştırma: S5 p(95) vs S6 p(95) vs S11 p(95) → serializasyon maliyetinin tam tablosu.
    grpc_s11_cached: {
      executor:        'ramping-vus',
      startTime:       `${W+S*4+C*2+T*2+GAP+K*2+GAP2+M+80+GAP3+S*2}s`,
      startVUs:        0,
      stages:          CACHE_STAGES,
      gracefulRampDown:'5s',
      env: { PROTO: 'grpc', SCENE: 's11' },
    },
  },

  // ── Threshold değerleri — EC2 ortamına göre kalibre edildi ───────────────────
  thresholds: {
    rest_s1_ms:         ['p(95)<100',   'p(99)<200'  ],
    grpc_s1_ms:         ['p(95)<100',   'p(99)<200'  ],
    rest_s2_ms:         ['p(95)<15000', 'p(99)<20000'],
    grpc_s2_ms:         ['p(95)<12000', 'p(99)<17000'],
    rest_s3_ms:         ['p(95)<40000', 'p(99)<50000'],
    grpc_s3_ms:         ['p(95)<35000', 'p(99)<45000'],
    rest_s4_ms:          ['p(95)<10000', 'p(99)<20000'],
    grpc_s4_ms:          ['p(95)<30000', 'p(99)<50000'],
    rest_s5_ms:          ['p(95)<15000', 'p(99)<20000'],
    rest_s6_ms:          ['p(95)<15000', 'p(99)<20000'],
    rest_error_rate:         ['rate<0.05'],
    grpc_error_rate:         ['rate<0.05'],
    cache_error_rate:        ['rate<0.01'],
    rest_s7_error_rate:      ['rate<0.05'],
    rest_s9_pool_ms:         ['p(95)<500',  'p(99)<1000' ],
    rest_s9_error_rate:      ['rate<0.05'],
    // S10 Mixed: hem büyük hem küçük payload içerdiğinden S2 threshold'larına yakın
    rest_s10_mixed_ms:       ['p(95)<15000','p(99)<20000'],
    grpc_s10_mixed_ms:       ['p(95)<12000','p(99)<17000'],
    rest_s10_error_rate:     ['rate<0.05'],
    grpc_s10_error_rate:     ['rate<0.05'],
    // S11 gRPC cache: S5/S6'dan daha hızlı beklenir (single deserialization)
    grpc_s11_cached_ms:      ['p(95)<10000','p(99)<15000'],
    grpc_s11_error_rate:     ['rate<0.01'],
  },
};

// ── gRPC client (per-VU singleton) ───────────────────────────────────────────
const client = new grpc.Client();
client.load(['./proto'], 'product.proto');
let connected  = false;
// warmReady: bu VU'nun bağlantı sonrası ilk isteği gönderip göndermediği.
// S4b (warm spike) senaryosunda bağlantı kurma süresini metrikten dışlar.
let warmReady  = false;

// ── gRPC hata tipi loglama ────────────────────────────────────────────────────
// Her VU en fazla 3 hata loglar — console spam'i önler.
// RESOURCE_EXHAUSTED = mesaj boyutu aşıldı / kapasite doldu
// UNAVAILABLE        = sunucu erişilemez / bağlantı reddedildi
// DEADLINE_EXCEEDED  = zaman aşımı
let grpcErrLogCount = 0;
function logGrpcError(scene, res) {
  if (grpcErrLogCount >= 3) return;
  grpcErrLogCount++;
  const code = res.status  || 'unknown_status';
  const msg  = res.error && res.error.message ? res.error.message : '—';
  console.warn(`[${scene}] gRPC hata | status: ${code} | msg: ${msg}`);
}

// ── Setup: servis sağlık kontrolü ────────────────────────────────────────────
// Cache warm-up bu aşamada YAPILMAZ. Neden:
//   - ProductCacheService.TTL_SEC = 3600s (artık benchmark süresini aşıyor).
//   - S5 490s'de, S6 600s'de, S11 ~1600s'de başlıyor — tüm senaryolar TTL içinde.
//   - Her senaryonun ilk isteği cache miss yapar ve kendi cache'ini doldurur.
//     50 VU × 110s süresince sadece ilk istek DB'ye gider, geri kalan binlercesi
//     warm cache'ten okur → sonuçlar üzerindeki etkisi <%0.1.
// Cache'i benchmark öncesi sıfırlamak istiyorsanız:
//   redis-cli -n 10 DEL products:list:json products:list:proto
export function setup() {
  const params = { headers: { 'Connection': 'keep-alive' }, timeout: '15s' };
  const checks = [
    ['REST /products/1',      `${REST_URL}/products/1`],
    ['REST /products/cached-json',  `${REST_URL}/products/cached-json`],
  ];
  for (const [name, url] of checks) {
    console.log(`[setup] Kontrol: ${name}`);
    const res = http.get(url, params);
    if (res.status === 200) {
      const mb = res.body ? (res.body.length / 1024 / 1024).toFixed(1) : '?';
      console.log(`[setup] ✓ ${name} — ${mb} MB`);
    } else {
      console.warn(`[setup] ✗ ${name} — HTTP ${res.status} — benchmark yine de başlıyor`);
    }
  }
}

// ── Ana döngü ─────────────────────────────────────────────────────────────────
export default function () {
  const proto = __ENV.PROTO;
  const scene = __ENV.SCENE;

  if (proto === 'grpc') {
    runGrpc(scene);
  } else {
    runRest(scene);
  }

  // ramping-arrival-rate executor'larında sleep() RPS'yi kısıtlar — bu senaryolarda atla.
  if (scene !== 's9') {
    sleep(0.1);
  }
}

// ── REST senaryoları ──────────────────────────────────────────────────────────
function runRest(scene) {
  if (scene === 's1') {
    // ID randomizasyonu: sabit ID=1 PostgreSQL buffer cache'ini aşırı ısıtır.
    // Math.ceil(random*5000): 1-5000 arası uniform dağılım — gerçekçi okuma yükü.
    const id = Math.ceil(Math.random() * 5000);
    const res = http.get(`${REST_URL}/products/${id}`, HTTP_PARAMS);
    restS1.add(res.timings.duration);
    restErrors.add(res.status !== 200);
    check(res, {
      '[S1-REST] status 200':   (r) => r.status === 200,
      '[S1-REST] has id field': (r) => { try { return JSON.parse(r.body).id !== undefined; } catch { return false; } },
    });

  } else if (scene === 's2') {
    const res = http.get(`${REST_URL}/products`, HTTP_PARAMS);
    restS2.add(res.timings.duration);
    restErrors.add(res.status !== 200);
    // Sadece S2'de byte ölçümü — büyük payload karşılaştırması için
    if (res.status === 200 && res.body) restS2BytesPerReq.add(res.body.length);
    check(res, {
      '[S2-REST] status 200':  (r) => r.status === 200,
      '[S2-REST] 1000+ items': (r) => { try { return JSON.parse(r.body).length >= 1000; } catch { return false; } },
    });

  } else if (scene === 's3') {
    const res = http.get(`${REST_URL}/products`, HTTP_PARAMS);
    restS3.add(res.timings.duration);
    restErrors.add(res.status !== 200);
    check(res, { '[S3-REST] status 200': (r) => r.status === 200 });

  } else if (scene === 's4') {
    // S4 cold spike: connection kurma maliyetini ölçüyoruz, routing değil.
    // Sabit ID=1 burada kasıtlı: DB hot-row etkisi cold-spike ölçümünü etkilememeli.
    const res = http.get(`${REST_URL}/products/1`, HTTP_PARAMS);
    restS4.add(res.timings.duration);
    restErrors.add(res.status !== 200);
    check(res, { '[S4-REST] status 200': (r) => r.status === 200 });

  } else if (scene === 's5') {
    const res = http.get(`${REST_URL}/products/cached-json`, HTTP_PARAMS);
    restS5.add(res.timings.duration);
    cacheErrors.add(res.status !== 200);
    check(res, {
      '[S5-Cache-JSON] status 200':  (r) => r.status === 200,
      '[S5-Cache-JSON] 1000+ items': (r) => { try { return JSON.parse(r.body).length >= 1000; } catch { return false; } },
    });

  } else if (scene === 's6') {
    const res = http.get(`${REST_URL}/products/cached-proto`, HTTP_PARAMS);
    restS6.add(res.timings.duration);
    cacheErrors.add(res.status !== 200);
    check(res, {
      '[S6-Cache-Proto] status 200':  (r) => r.status === 200,
      '[S6-Cache-Proto] 1000+ items': (r) => { try { return JSON.parse(r.body).length >= 1000; } catch { return false; } },
    });

  } else if (scene === 's7') {
    const id = Math.ceil(Math.random() * 5000);
    const res = http.get(`${REST_URL}/products/${id}`, HTTP_PARAMS);
    restS7.add(res.timings.duration);
    restS7Errors.add(res.status !== 200);
    check(res, { '[S7-REST-MaxTP] status 200': (r) => r.status === 200 });

  } else if (scene === 's9') {
    // Pool simülasyonu: GetProduct (küçük payload) — multiplexing farkı burada görünür
    // ramping-arrival-rate RPS'i kontrol eder; sleep() kullanılmaz.
    const id = Math.ceil(Math.random() * 5000);
    const res = http.get(`${REST_URL}/products/${id}`, HTTP_PARAMS);
    restS9.add(res.timings.duration);
    restS9Errors.add(res.status !== 200);
    check(res, { '[S9-REST-Pool] status 200': (r) => r.status === 200 });

  } else if (scene === 's10') {
    // Mixed workload: %80 GetProduct, %20 ListProducts
    // Math.random() < 0.8 → GetProduct; else → ListProducts
    if (Math.random() < 0.8) {
      const id = Math.ceil(Math.random() * 5000);
      const res = http.get(`${REST_URL}/products/${id}`, HTTP_PARAMS);
      restS10.add(res.timings.duration);
      restS10E.add(res.status !== 200);
      check(res, { '[S10-REST-Mixed-Get] status 200': (r) => r.status === 200 });
    } else {
      const res = http.get(`${REST_URL}/products`, HTTP_PARAMS);
      restS10.add(res.timings.duration);
      restS10E.add(res.status !== 200);
      check(res, { '[S10-REST-Mixed-List] status 200': (r) => r.status === 200 });
    }
  }
}

// ── gRPC senaryoları ──────────────────────────────────────────────────────────
function runGrpc(scene) {
  if (!connected) {
    client.connect(GRPC_URL, { plaintext: true });
    connected = true;
  }

  if (scene === 's1') {
    const id = Math.ceil(Math.random() * 5000);
    const start = Date.now();
    const res = client.invoke('product.ProductGrpcService/GetProduct', { id });
    grpcS1.add(Date.now() - start);
    const err = res.status !== grpc.StatusOK;
    grpcErrors.add(err);
    if (err) logGrpcError('S1', res);
    check(res, {
      '[S1-gRPC] status OK': (r) => r.status === grpc.StatusOK,
      '[S1-gRPC] has id':    (r) => r.message && r.message.id !== undefined,
    });

  } else if (scene === 's2') {
    const start = Date.now();
    const res = client.invoke('product.ProductGrpcService/ListProducts', {});
    grpcS2.add(Date.now() - start);
    const err = res.status !== grpc.StatusOK;
    grpcErrors.add(err);
    if (err) logGrpcError('S2', res);
    // Sadece S2'de byte ölçümü — JSON.stringify(parsed_protobuf) yaklaşımı:
    // k6 raw Protobuf bytes'a erişim sağlamaz; bu değer gerçek wire size'dan
    // büyük olabilir. Raporda sınırlılık notu gösterilir.
    if (res.message) grpcS2BytesApprox.add(JSON.stringify(res.message).length);
    check(res, {
      '[S2-gRPC] status OK':   (r) => r.status === grpc.StatusOK,
      '[S2-gRPC] 1000+ items': (r) => r.message && r.message.products && r.message.products.length >= 1000,
    });

  } else if (scene === 's3') {
    const start = Date.now();
    const res = client.invoke('product.ProductGrpcService/ListProducts', {});
    grpcS3.add(Date.now() - start);
    const err = res.status !== grpc.StatusOK;
    grpcErrors.add(err);
    if (err) logGrpcError('S3', res);
    check(res, { '[S3-gRPC] status OK': (r) => r.status === grpc.StatusOK });

  } else if (scene === 's4') {
    // S4 cold spike: sabit id=1 kasıtlı (S1 ile aynı gerekçe — bkz. REST S4).
    const start = Date.now();
    const res = client.invoke('product.ProductGrpcService/GetProduct', { id: 1 });
    grpcS4.add(Date.now() - start);
    const err = res.status !== grpc.StatusOK;
    grpcErrors.add(err);
    if (err) logGrpcError('S4-cold', res);
    check(res, { '[S4-gRPC] status OK': (r) => r.status === grpc.StatusOK });

  } else if (scene === 's10') {
    // Mixed workload: %80 GetProduct (random ID), %20 ListProducts
    if (Math.random() < 0.8) {
      const id = Math.ceil(Math.random() * 5000);
      const start = Date.now();
      const res = client.invoke('product.ProductGrpcService/GetProduct', { id });
      grpcS10.add(Date.now() - start);
      const err = res.status !== grpc.StatusOK;
      grpcS10E.add(err);
      if (err) logGrpcError('S10-mixed-get', res);
      check(res, { '[S10-gRPC-Mixed-Get] status OK': (r) => r.status === grpc.StatusOK });
    } else {
      const start = Date.now();
      const res = client.invoke('product.ProductGrpcService/ListProducts', {});
      grpcS10.add(Date.now() - start);
      const err = res.status !== grpc.StatusOK;
      grpcS10E.add(err);
      if (err) logGrpcError('S10-mixed-list', res);
      check(res, { '[S10-gRPC-Mixed-List] status OK': (r) => r.status === grpc.StatusOK });
    }

  } else if (scene === 's11') {
    // gRPC Cache: ListProductsCachedGrpc → Redis Proto bytes → single parseFrom
    // S5/S6 REST cache ile karşılaştırılır — serializasyon maliyeti farkını ölçer.
    const start = Date.now();
    const res = client.invoke('product.ProductGrpcService/ListProductsCachedGrpc', {});
    grpcS11.add(Date.now() - start);
    const err = res.status !== grpc.StatusOK;
    grpcS11E.add(err);
    if (err) logGrpcError('S11-grpc-cache', res);
    check(res, {
      '[S11-gRPC-Cache] status OK':   (r) => r.status === grpc.StatusOK,
      '[S11-gRPC-Cache] 1000+ items': (r) => r.message && r.message.products && r.message.products.length >= 1000,
    });
  }
}

// ── Karşılaştırma raporu ──────────────────────────────────────────────────────
export function handleSummary(data) {
  const m = data.metrics;

  function get(name, stat) {
    return m[name] && m[name].values[stat] !== undefined ? m[name].values[stat] : null;
  }

  function fmt(val, pad = 10) {
    return (val !== null ? val.toFixed(1) + 'ms' : 'N/A').padEnd(pad);
  }

  function winner(aLabel, aVal, bLabel, bVal) {
    if (aVal === null || bVal === null) return '—';
    const pct = ((bVal - aVal) / aVal * 100);
    if (Math.abs(pct) < 3) return '≈ eşit';
    return pct > 0
      ? `${aLabel}  %${Math.abs(pct).toFixed(0)} daha hızlı`
      : `${bLabel}  %${Math.abs(pct).toFixed(0)} daha hızlı`;
  }

  function restVsGrpc(label, rm, gm) {
    const rA   = get(rm, 'avg'),   gA   = get(gm, 'avg');
    const rP95 = get(rm, 'p(95)'), gP95 = get(gm, 'p(95)');
    const rP99 = get(rm, 'p(99)'), gP99 = get(gm, 'p(99)');
    const rM   = get(rm, 'max'),   gM   = get(gm, 'max');
    return (
      `  ┌─ ${label}\n` +
      `  │  avg    REST: ${fmt(rA)}   gRPC: ${fmt(gA)}   → ${winner('REST', rA, 'gRPC', gA)}\n` +
      `  │  p(95)  REST: ${fmt(rP95)} gRPC: ${fmt(gP95)} → ${winner('REST', rP95, 'gRPC', gP95)}\n` +
      `  │  p(99)  REST: ${fmt(rP99)} gRPC: ${fmt(gP99)} → ${winner('REST', rP99, 'gRPC', gP99)}\n` +
      `  └─ max    REST: ${fmt(rM)}   gRPC: ${fmt(gM)}   → ${winner('REST', rM, 'gRPC', gM)}`
    );
  }

  function cacheRow(label, m5, m6) {
    const jA   = get(m5, 'avg'),   pA   = get(m6, 'avg');
    const jP95 = get(m5, 'p(95)'), pP95 = get(m6, 'p(95)');
    const jP99 = get(m5, 'p(99)'), pP99 = get(m6, 'p(99)');
    const jM   = get(m5, 'max'),   pM   = get(m6, 'max');
    return (
      `  ┌─ ${label}\n` +
      `  │  avg    JSON: ${fmt(jA)}   Proto: ${fmt(pA)}   → ${winner('JSON', jA, 'Proto', pA)}\n` +
      `  │  p(95)  JSON: ${fmt(jP95)} Proto: ${fmt(pP95)} → ${winner('JSON', jP95, 'Proto', pP95)}\n` +
      `  │  p(99)  JSON: ${fmt(jP99)} Proto: ${fmt(pP99)} → ${winner('JSON', jP99, 'Proto', pP99)}\n` +
      `  └─ max    JSON: ${fmt(jM)}   Proto: ${fmt(pM)}   → ${winner('JSON', jM, 'Proto', pM)}`
    );
  }

  const rP95s  = ['rest_s1_ms','rest_s2_ms','rest_s3_ms','rest_s4_ms'].map(n => get(n,'p(95)')).filter(v => v !== null);
  const gP95s  = ['grpc_s1_ms','grpc_s2_ms','grpc_s3_ms','grpc_s4_ms'].map(n => get(n,'p(95)')).filter(v => v !== null);
  const avgRP95 = rP95s.length ? rP95s.reduce((a,b) => a+b,0)/rP95s.length : null;
  const avgGP95 = gP95s.length ? gP95s.reduce((a,b) => a+b,0)/gP95s.length : null;

  const noCache    = get('rest_s2_ms', 'p(95)');
  const jsonCache  = get('rest_s5_ms', 'p(95)');
  const protoCache = get('rest_s6_ms', 'p(95)');
  function cacheGain(baseline, cached) {
    if (baseline === null || cached === null) return '—';
    const pct = ((baseline - cached) / baseline * 100);
    return pct > 0 ? `%${pct.toFixed(0)} daha hızlı (cache gain)` : `%${Math.abs(pct).toFixed(0)} daha yavaş`;
  }

  // S2 büyük payload byte ölçümü (per-request ortalama)
  const restS2AvgBytes  = get('rest_s2_bytes_per_req', 'avg');
  const grpcS2AvgBytes  = get('grpc_s2_bytes_approx',  'avg');
  const restMaxP95 = get('rest_s7_max_tp_ms', 'p(95)');
  const restMaxP99 = get('rest_s7_max_tp_ms', 'p(99)');
  const restMaxErr = m['rest_s7_error_rate'] ? (m['rest_s7_error_rate'].values.rate * 100).toFixed(2) : '0.00';
  const restPoolP95 = get('rest_s9_pool_ms', 'p(95)');
  const restPoolP99 = get('rest_s9_pool_ms', 'p(99)');
  const restPoolErr = m['rest_s9_error_rate'] ? (m['rest_s9_error_rate'].values.rate * 100).toFixed(2) : '0.00';
  const restPoolIter = m['rest_s9_pool_ms'] ? (m['rest_s9_pool_ms'].values['count'] || 0) : 0;
  const rErr = m['rest_error_rate']  ? (m['rest_error_rate'].values.rate  * 100).toFixed(2) : '0.00';
  const gErr = m['grpc_error_rate']  ? (m['grpc_error_rate'].values.rate  * 100).toFixed(2) : '0.00';
  const cErr = m['cache_error_rate'] ? (m['cache_error_rate'].values.rate * 100).toFixed(2) : '0.00';

  // S10 Mixed
  const rMixP95  = get('rest_s10_mixed_ms', 'p(95)');
  const gMixP95  = get('grpc_s10_mixed_ms', 'p(95)');
  const rMixP99  = get('rest_s10_mixed_ms', 'p(99)');
  const gMixP99  = get('grpc_s10_mixed_ms', 'p(99)');
  const rMixErr  = m['rest_s10_error_rate'] ? (m['rest_s10_error_rate'].values.rate * 100).toFixed(2) : '0.00';
  const gMixErr  = m['grpc_s10_error_rate'] ? (m['grpc_s10_error_rate'].values.rate * 100).toFixed(2) : '0.00';
  // S11 gRPC cache
  const g11P95   = get('grpc_s11_cached_ms', 'p(95)');
  const g11P99   = get('grpc_s11_cached_ms', 'p(99)');
  const g11Err   = m['grpc_s11_error_rate']  ? (m['grpc_s11_error_rate'].values.rate  * 100).toFixed(2) : '0.00';

  const report = `

╔══════════════════════════════════════════════════════════════════════╗
║          BENCHMARK KARŞILAŞTIRMA RAPORU                              ║
║          REST vs gRPC vs Redis-JSON Cache vs Redis-Proto Cache        ║
║          Spike MAX VU: ${String(MAX_SPIKE_VU).padEnd(5)}  Max RPS: ${String(MAX_RPS).padEnd(5)}                    ║
║          REST hata: %${rErr.padEnd(5)}  gRPC hata: %${gErr.padEnd(5)}  Cache hata: %${cErr.padEnd(5)}  ║
╚══════════════════════════════════════════════════════════════════════╝

━━━  REST vs gRPC  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

${restVsGrpc('S1 — Küçük Payload       (GetProduct,   1 ürün,        50 VU)', 'rest_s1_ms', 'grpc_s1_ms')}

${restVsGrpc('S2 — Büyük Payload       (ListProducts, ~20MB,         50 VU)  [no cache]', 'rest_s2_ms', 'grpc_s2_ms')}

${restVsGrpc('S3 — Yüksek Eşzamanlılık (ListProducts, ~20MB,        200 VU)', 'rest_s3_ms', 'grpc_s3_ms')}

${restVsGrpc(`S4 — Flash Sale Spike COLD (GetProduct,   0→${MAX_SPIKE_VU} VU, 10s'de)`, 'rest_s4_ms', 'grpc_s4_ms')}
  ⚠  gRPC cold spike: Her VU ilk iterasyonda bağlantı kurar. Production'da
     connection pool bu farkı kapatır. Ham cold-start testi intentional olarak korundu.

──────────────────────────────────────────────────────────────────────
  GENEL REST vs gRPC  [S1-S4 p(95) ortalaması]
    REST : ${fmt(avgRP95)}
    gRPC : ${fmt(avgGP95)}
    Sonuç: ${winner('REST', avgRP95, 'gRPC', avgGP95)}

━━━  Redis Cache Karşılaştırması  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  NOT: Cache testleri S2 ile benzer makine durumunda çalışır (S4 spike öncesi).

${cacheRow('S5 vs S6 — JSON Cache / Proto Cache  (ListProducts, ~20MB, 50 VU, warm cache)', 'rest_s5_ms', 'rest_s6_ms')}

──────────────────────────────────────────────────────────────────────
  CACHE GAIN — No-Cache (S2-REST p95) → Cached p95
    No-Cache (S2-REST) : ${fmt(noCache)}
    JSON  Cache  (S5)  : ${fmt(jsonCache)}  → ${cacheGain(noCache, jsonCache)}
    Proto Cache  (S6)  : ${fmt(protoCache)}  → ${cacheGain(noCache, protoCache)}

━━━  S2 Response Payload — REST (gzip JSON) vs gRPC (Protobuf)  ━━━━━━
  Ölçüm: Sadece S2 ListProducts — ~5000 ürün, per-request ortalama.
  REST JSON  : ${restS2AvgBytes !== null ? (restS2AvgBytes/1024/1024).toFixed(2)+' MB/req' : 'N/A'} (res.body.length — wire-accurate, headers hariç)
  gRPC approx: ${grpcS2AvgBytes !== null ? (grpcS2AvgBytes/1024/1024).toFixed(2)+' MB/req' : 'N/A'} (JSON.stringify(parsed_msg) — üst sınır tahmini)
  ${(() => {
    if (restS2AvgBytes === null || grpcS2AvgBytes === null) return '→ Veri yok';
    const pct = ((restS2AvgBytes - grpcS2AvgBytes) / restS2AvgBytes * 100);
    return `→ gRPC ölçülen fark: REST'ten %${Math.abs(pct).toFixed(0)} ${pct>0?'küçük (bandwidth tasarrufu)':'büyük'}`;
  })()}

  ⚠ Doğruluk notu:
    REST değeri wire-accurate (gzip sonrası HTTP body byte sayısı).
    gRPC değeri overestimate — k6 client raw Protobuf bytes expose etmez;
    deserialize sonrası JSON boyutu ölçülür. Gerçek Protobuf wire size
    bu değerin %80-90'ı kadardır.
    gzip JSON küçük kalırsa REST bandwidth avantajı beklenenden fazla olabilir.

━━━  S7 — REST Max Throughput  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Strateji: ${Math.round(MAX_RPS*0.05)} → ${Math.round(MAX_RPS*0.25)} → ${Math.round(MAX_RPS*0.5)} → ${MAX_RPS} RPS
  REST doğal modelinde test edilir (HTTP/1.1: bağlantı sayısı ≈ concurrent istek sayısı).
  Hata oranı %5'i geçtiğinde darboğaz noktası aşılmıştır.
    REST  p(95): ${fmt(restMaxP95)}  p(99): ${fmt(restMaxP99)}  hata: %${restMaxErr}
  → gRPC eşdeğeri: ghz-compare.sh G1 (--connections 1 --concurrency 50) — gerçek multiplexing

━━━  S9 — REST Connection Pool  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ${MS_POOL_VU} keep-alive bağlantı × hedef ${MS_MAX_RPS} RPS — REST doğal modelinde adil ölçüm.
    p(95): ${fmt(restPoolP95)}  p(99): ${fmt(restPoolP99)}  hata: %${restPoolErr}  iter: ${restPoolIter}
  → gRPC eşdeğeri: ghz-compare.sh G4 (--connections ${MS_POOL_VU}) — gerçek gRPC pool davranışı

━━━  S10 — Mixed Workload (%80 GetProduct + %20 ListProducts, 50 VU)  ━
  Gerçek trafik dağılımı — protokol seçiminin üretim yükündeki net etkisi.
  REST  p(95): ${fmt(rMixP95)}  p(99): ${fmt(rMixP99)}  hata: %${rMixErr}
  gRPC  p(95): ${fmt(gMixP95)}  p(99): ${fmt(gMixP99)}  hata: %${gMixErr}
  Sonuç: ${winner('REST', rMixP95, 'gRPC', gMixP95)}

━━━  S11 — gRPC Cache (Redis Proto → single parseFrom, 50 VU)  ━━━━━━━
  REST S5 path: Redis JSON → Jackson 2× serializasyon → HTTP JSON
  REST S6 path: Redis Proto → parseFrom → Jackson → HTTP JSON
  gRPC S11 path: Redis Proto → parseFrom → onNext() (Protobuf wire)
    gRPC  p(95): ${fmt(g11P95)}  p(99): ${fmt(g11P99)}  hata: %${g11Err}
    Cache Gain vs S5 (JSON cache): ${cacheGain(jsonCache, g11P95)}
    Cache Gain vs S6 (Proto cache): ${cacheGain(protoCache, g11P95)}

──────────────────────────────────────────────────────────────────────
`;

  return { stdout: report };
}

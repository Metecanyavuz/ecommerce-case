import http from 'k6/http';
import grpc from 'k6/net/grpc';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const REST_URL     = __ENV.REST_URL     || 'http://product-service.railway.internal:8080';
const GRPC_URL     = __ENV.GRPC_URL     || 'product-service.railway.internal:9090';
const MAX_SPIKE_VU = parseInt(__ENV.MAX_SPIKE_VU || '500');

// ── Per-scenario latency metrics ──────────────────────────────────────────────
const restS1 = new Trend('rest_s1_ms', true);   // S1: küçük payload
const grpcS1 = new Trend('grpc_s1_ms', true);
const restS2 = new Trend('rest_s2_ms', true);   // S2: büyük payload (no cache)
const grpcS2 = new Trend('grpc_s2_ms', true);
const restS3 = new Trend('rest_s3_ms', true);   // S3: yüksek eşzamanlılık
const grpcS3 = new Trend('grpc_s3_ms', true);
const restS4 = new Trend('rest_s4_ms', true);   // S4: flash sale spike
const grpcS4 = new Trend('grpc_s4_ms', true);
const restS5 = new Trend('rest_s5_ms', true);   // S5: Redis JSON cache (warm)
const restS6 = new Trend('rest_s6_ms', true);   // S6: Redis Protobuf cache (warm)

const restErrors  = new Rate('rest_error_rate');
const grpcErrors  = new Rate('grpc_error_rate');
const cacheErrors = new Rate('cache_error_rate');

// ── Stage şablonları ──────────────────────────────────────────────────────────
const STD_STAGES = [
  { duration: '10s', target: 10  },  // warm-up
  { duration: '30s', target: 50  },  // ramp-up
  { duration: '60s', target: 50  },  // sustained
  { duration: '10s', target: 0   },  // ramp-down
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

// Cache senaryoları için daha kısa warm-up — setup() zaten cache'i ısıtıyor
const CACHE_STAGES = [
  { duration: '5s',  target: 10  },  // minimal warm-up (cache zaten hazır)
  { duration: '30s', target: 50  },  // ramp-up
  { duration: '60s', target: 50  },  // sustained (cache hit ölçümü)
  { duration: '10s', target: 0   },  // ramp-down
];

// Blok süreleri (graceful dahil)
const S = 115;  // STD    blok: 110s + 5s graceful
const T = 75;   // STRESS blok:  70s + 5s graceful
const K = 55;   // SPIKE  blok:  50s + 5s graceful
const C = 110;  // CACHE  blok: 105s + 5s graceful

// startTime tablosu
// S1-REST: 0s      | S1-gRPC: 115s
// S2-REST: 230s    | S2-gRPC: 345s
// S3-REST: 460s    | S3-gRPC: 535s
// S4-REST: 610s    | S4-gRPC: 665s
// S5-Cache-JSON:   720s
// S6-Cache-Proto:  830s
// Toplam: ~940s ≈ ~16 dakika

export const options = {
  scenarios: {
    // S1: Küçük payload — ürün detay sayfası
    rest_s1_small:    { executor: 'ramping-vus', startTime: `${S*0}s`,         startVUs: 0, stages: STD_STAGES,    gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's1' } },
    grpc_s1_small:    { executor: 'ramping-vus', startTime: `${S*1}s`,         startVUs: 0, stages: STD_STAGES,    gracefulRampDown: '5s', env: { PROTO: 'grpc', SCENE: 's1' } },
    // S2: Büyük payload — katalog sync (~20MB), cache YOK
    rest_s2_large:    { executor: 'ramping-vus', startTime: `${S*2}s`,         startVUs: 0, stages: STD_STAGES,    gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's2' } },
    grpc_s2_large:    { executor: 'ramping-vus', startTime: `${S*3}s`,         startVUs: 0, stages: STD_STAGES,    gracefulRampDown: '5s', env: { PROTO: 'grpc', SCENE: 's2' } },
    // S3: Yüksek eşzamanlılık — 200 VU sabit yük
    rest_s3_stress:   { executor: 'ramping-vus', startTime: `${S*4}s`,         startVUs: 0, stages: STRESS_STAGES, gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's3' } },
    grpc_s3_stress:   { executor: 'ramping-vus', startTime: `${S*4+T}s`,       startVUs: 0, stages: STRESS_STAGES, gracefulRampDown: '5s', env: { PROTO: 'grpc', SCENE: 's3' } },
    // S4: Flash sale spike
    rest_s4_spike:    { executor: 'ramping-vus', startTime: `${S*4+T*2}s`,     startVUs: 0, stages: SPIKE_STAGES,  gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's4' } },
    grpc_s4_spike:    { executor: 'ramping-vus', startTime: `${S*4+T*2+K}s`,   startVUs: 0, stages: SPIKE_STAGES,  gracefulRampDown: '5s', env: { PROTO: 'grpc', SCENE: 's4' } },
    // S5: Redis JSON cache (warm) — setup() cache'i önceden ısıttı
    cache_s5_json:    { executor: 'ramping-vus', startTime: `${S*4+T*2+K*2}s`, startVUs: 0, stages: CACHE_STAGES,  gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's5' } },
    // S6: Redis Protobuf cache (warm) — aynı endpoint, farklı Redis serializasyonu
    cache_s6_proto:   { executor: 'ramping-vus', startTime: `${S*4+T*2+K*2+C}s`, startVUs: 0, stages: CACHE_STAGES, gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's6' } },
  },
  thresholds: {
    rest_s1_ms:        ['p(95)<500'],
    grpc_s1_ms:        ['p(95)<500'],
    rest_s2_ms:        ['p(95)<2000'],
    grpc_s2_ms:        ['p(95)<2000'],
    rest_s3_ms:        ['p(95)<5000'],
    grpc_s3_ms:        ['p(95)<5000'],
    rest_s4_ms:        ['p(95)<10000'],
    grpc_s4_ms:        ['p(95)<10000'],
    rest_s5_ms:        ['p(95)<200'],   // cache hit — DB yok, çok daha hızlı olmalı
    rest_s6_ms:        ['p(95)<200'],
    rest_error_rate:   ['rate<0.10'],
    grpc_error_rate:   ['rate<0.10'],
    cache_error_rate:  ['rate<0.01'],   // cache senaryolarında hata toleransı düşük
  },
};

// ── gRPC client (per-VU singleton) ───────────────────────────────────────────
const client = new grpc.Client();
client.load(['./proto'], 'product.proto');
let connected = false;

// ── Cache warm-up — senaryolar başlamadan önce bir kez çalışır ───────────────
// Redis'e ilk yazma (cache miss) burada gerçekleşir.
// S5 ve S6 senaryolarındaki tüm istekler cache hit olarak ölçülür.
export function setup() {
  const jsonRes  = http.get(`${REST_URL}/products/cached-json`);
  const protoRes = http.get(`${REST_URL}/products/cached-proto`);
  if (jsonRes.status !== 200 || protoRes.status !== 200) {
    console.warn(`[setup] Cache warm-up başarısız — JSON: ${jsonRes.status}, Proto: ${protoRes.status}`);
  } else {
    console.log('[setup] Her iki cache ısındı. S5/S6 senaryoları cache hit ölçecek.');
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

  sleep(0.1);
}

// ── REST senaryoları ──────────────────────────────────────────────────────────
function runRest(scene) {
  if (scene === 's1') {
    const res = http.get(`${REST_URL}/products/1`);
    restS1.add(res.timings.duration);
    restErrors.add(res.status !== 200);
    check(res, {
      '[S1-REST] status 200':   (r) => r.status === 200,
      '[S1-REST] has id field': (r) => { try { return JSON.parse(r.body).id !== undefined; } catch { return false; } },
    });

  } else if (scene === 's2') {
    const res = http.get(`${REST_URL}/products`);
    restS2.add(res.timings.duration);
    restErrors.add(res.status !== 200);
    check(res, {
      '[S2-REST] status 200':  (r) => r.status === 200,
      '[S2-REST] 1000+ items': (r) => { try { return JSON.parse(r.body).length >= 1000; } catch { return false; } },
    });

  } else if (scene === 's3') {
    const res = http.get(`${REST_URL}/products`);
    restS3.add(res.timings.duration);
    restErrors.add(res.status !== 200);
    check(res, { '[S3-REST] status 200': (r) => r.status === 200 });

  } else if (scene === 's4') {
    const res = http.get(`${REST_URL}/products/1`);
    restS4.add(res.timings.duration);
    restErrors.add(res.status !== 200);
    check(res, { '[S4-REST] status 200': (r) => r.status === 200 });

  } else if (scene === 's5') {
    // Redis JSON cache — GET /products/cached-json
    // Sunucu: ObjectMapper.readValue(redisBytes) → List<ProductResponse> → JSON response
    const res = http.get(`${REST_URL}/products/cached-json`);
    restS5.add(res.timings.duration);
    cacheErrors.add(res.status !== 200);
    check(res, {
      '[S5-Cache-JSON] status 200':  (r) => r.status === 200,
      '[S5-Cache-JSON] 1000+ items': (r) => { try { return JSON.parse(r.body).length >= 1000; } catch { return false; } },
    });

  } else if (scene === 's6') {
    // Redis Protobuf cache — GET /products/cached-proto
    // Sunucu: ProductListGrpcResponse.parseFrom(redisBytes) → List<ProductResponse> → JSON response
    const res = http.get(`${REST_URL}/products/cached-proto`);
    restS6.add(res.timings.duration);
    cacheErrors.add(res.status !== 200);
    check(res, {
      '[S6-Cache-Proto] status 200':  (r) => r.status === 200,
      '[S6-Cache-Proto] 1000+ items': (r) => { try { return JSON.parse(r.body).length >= 1000; } catch { return false; } },
    });
  }
}

// ── gRPC senaryoları ──────────────────────────────────────────────────────────
function runGrpc(scene) {
  if (!connected) {
    client.connect(GRPC_URL, { plaintext: true });
    connected = true;
  }

  if (scene === 's1') {
    const start = Date.now();
    const res = client.invoke('product.ProductGrpcService/GetProduct', { id: 1 });
    grpcS1.add(Date.now() - start);
    grpcErrors.add(res.status !== grpc.StatusOK);
    check(res, {
      '[S1-gRPC] status OK': (r) => r.status === grpc.StatusOK,
      '[S1-gRPC] has id':    (r) => r.message && r.message.id !== undefined,
    });

  } else if (scene === 's2') {
    const start = Date.now();
    const res = client.invoke('product.ProductGrpcService/ListProducts', {});
    grpcS2.add(Date.now() - start);
    grpcErrors.add(res.status !== grpc.StatusOK);
    check(res, {
      '[S2-gRPC] status OK':   (r) => r.status === grpc.StatusOK,
      '[S2-gRPC] 1000+ items': (r) => r.message && r.message.products && r.message.products.length >= 1000,
    });

  } else if (scene === 's3') {
    const start = Date.now();
    const res = client.invoke('product.ProductGrpcService/ListProducts', {});
    grpcS3.add(Date.now() - start);
    grpcErrors.add(res.status !== grpc.StatusOK);
    check(res, { '[S3-gRPC] status OK': (r) => r.status === grpc.StatusOK });

  } else if (scene === 's4') {
    const start = Date.now();
    const res = client.invoke('product.ProductGrpcService/GetProduct', { id: 1 });
    grpcS4.add(Date.now() - start);
    grpcErrors.add(res.status !== grpc.StatusOK);
    check(res, { '[S4-gRPC] status OK': (r) => r.status === grpc.StatusOK });
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
    const rA = get(rm, 'avg'),   gA = get(gm, 'avg');
    const rP = get(rm, 'p(95)'), gP = get(gm, 'p(95)');
    const rM = get(rm, 'max'),   gM = get(gm, 'max');
    return (
      `  ┌─ ${label}\n` +
      `  │  avg    REST: ${fmt(rA)} gRPC: ${fmt(gA)} → ${winner('REST', rA, 'gRPC', gA)}\n` +
      `  │  p(95)  REST: ${fmt(rP)} gRPC: ${fmt(gP)} → ${winner('REST', rP, 'gRPC', gP)}\n` +
      `  └─ max    REST: ${fmt(rM)} gRPC: ${fmt(gM)} → ${winner('REST', rM, 'gRPC', gM)}`
    );
  }

  function cacheRow(label, m5, m6) {
    const jA = get(m5, 'avg'),   pA = get(m6, 'avg');
    const jP = get(m5, 'p(95)'), pP = get(m6, 'p(95)');
    const jM = get(m5, 'max'),   pM = get(m6, 'max');
    return (
      `  ┌─ ${label}\n` +
      `  │  avg    JSON: ${fmt(jA)} Proto: ${fmt(pA)} → ${winner('JSON', jA, 'Proto', pA)}\n` +
      `  │  p(95)  JSON: ${fmt(jP)} Proto: ${fmt(pP)} → ${winner('JSON', jP, 'Proto', pP)}\n` +
      `  └─ max    JSON: ${fmt(jM)} Proto: ${fmt(pM)} → ${winner('JSON', jM, 'Proto', pM)}`
    );
  }

  // Genel REST vs gRPC p(95) ortalaması (S1-S4)
  const rP95s = ['rest_s1_ms','rest_s2_ms','rest_s3_ms','rest_s4_ms'].map(n => get(n,'p(95)')).filter(v => v !== null);
  const gP95s = ['grpc_s1_ms','grpc_s2_ms','grpc_s3_ms','grpc_s4_ms'].map(n => get(n,'p(95)')).filter(v => v !== null);
  const avgRP95 = rP95s.length ? rP95s.reduce((a,b) => a+b,0)/rP95s.length : null;
  const avgGP95 = gP95s.length ? gP95s.reduce((a,b) => a+b,0)/gP95s.length : null;

  // Cache vs No-Cache karşılaştırması (S2 = no-cache large payload)
  const noCache  = get('rest_s2_ms', 'p(95)');
  const jsonCache = get('rest_s5_ms', 'p(95)');
  const protoCache = get('rest_s6_ms', 'p(95)');
  function cacheGain(baseline, cached) {
    if (baseline === null || cached === null) return '—';
    const pct = ((baseline - cached) / baseline * 100);
    return pct > 0 ? `%${pct.toFixed(0)} daha hızlı (cache gain)` : `%${Math.abs(pct).toFixed(0)} daha yavaş`;
  }

  const rErr = m['rest_error_rate']  ? (m['rest_error_rate'].values.rate  * 100).toFixed(2) : '0.00';
  const gErr = m['grpc_error_rate']  ? (m['grpc_error_rate'].values.rate  * 100).toFixed(2) : '0.00';
  const cErr = m['cache_error_rate'] ? (m['cache_error_rate'].values.rate * 100).toFixed(2) : '0.00';

  const report = `

╔══════════════════════════════════════════════════════════════════════╗
║          BENCHMARK KARŞILAŞTIRMA RAPORU                              ║
║          REST vs gRPC vs Redis-JSON Cache vs Redis-Proto Cache        ║
║          Spike MAX VU: ${String(MAX_SPIKE_VU).padEnd(5)}                                      ║
║          REST hata: %${rErr.padEnd(5)}  gRPC hata: %${gErr.padEnd(5)}  Cache hata: %${cErr.padEnd(5)}  ║
╚══════════════════════════════════════════════════════════════════════╝

━━━  REST vs gRPC  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

${restVsGrpc('S1 — Küçük Payload      (GetProduct,   1 ürün,        50 VU)', 'rest_s1_ms', 'grpc_s1_ms')}

${restVsGrpc('S2 — Büyük Payload      (ListProducts, ~20MB,         50 VU)  [no cache]', 'rest_s2_ms', 'grpc_s2_ms')}

${restVsGrpc('S3 — Yüksek Eşzamanlılık (ListProducts, ~20MB,       200 VU)', 'rest_s3_ms', 'grpc_s3_ms')}

${restVsGrpc(`S4 — Flash Sale Spike    (GetProduct,   0→${MAX_SPIKE_VU} VU, 10s'de)`, 'rest_s4_ms', 'grpc_s4_ms')}

──────────────────────────────────────────────────────────────────────
  GENEL REST vs gRPC  [S1-S4 p(95) ortalaması]
    REST : ${fmt(avgRP95)}
    gRPC : ${fmt(avgGP95)}
    Sonuç: ${winner('REST', avgRP95, 'gRPC', avgGP95)}

━━━  Redis Cache Karşılaştırması  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

${cacheRow('S5 vs S6 — JSON Cache / Proto Cache  (ListProducts, ~20MB, 50 VU, warm cache)', 'rest_s5_ms', 'rest_s6_ms')}

──────────────────────────────────────────────────────────────────────
  CACHE GAIN — No-Cache (S2-REST p95) → Cached p95
    No-Cache (S2-REST) : ${fmt(noCache)}
    JSON  Cache (S5)   : ${fmt(jsonCache)}  → ${cacheGain(noCache, jsonCache)}
    Proto Cache (S6)   : ${fmt(protoCache)}  → ${cacheGain(noCache, protoCache)}
──────────────────────────────────────────────────────────────────────
`;

  return { stdout: report };
}

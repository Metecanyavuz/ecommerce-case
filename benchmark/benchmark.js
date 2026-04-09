import http from 'k6/http';
import grpc from 'k6/net/grpc';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const REST_URL      = __ENV.REST_URL       || 'http://product-service.railway.internal:8080';
const GRPC_URL      = __ENV.GRPC_URL       || 'product-service.railway.internal:9090';
const MAX_SPIKE_VU  = parseInt(__ENV.MAX_SPIKE_VU || '500');  // Railway: 500, AWS: 5000

// ── Per-scenario latency metrics ──────────────────────────────────────────
const restS1 = new Trend('rest_s1_ms', true);  // S1: küçük payload
const grpcS1 = new Trend('grpc_s1_ms', true);
const restS2 = new Trend('rest_s2_ms', true);  // S2: büyük payload
const grpcS2 = new Trend('grpc_s2_ms', true);
const restS3 = new Trend('rest_s3_ms', true);  // S3: yüksek eşzamanlılık
const grpcS3 = new Trend('grpc_s3_ms', true);
const restS4 = new Trend('rest_s4_ms', true);  // S4: flash sale spike
const grpcS4 = new Trend('grpc_s4_ms', true);

const restErrors = new Rate('rest_error_rate');
const grpcErrors = new Rate('grpc_error_rate');

// ── Senaryo süreleri ──────────────────────────────────────────────────────
// STD   : 10+30+60+10 = 110s + 5s graceful = 115s
// STRESS: 10+20+30+10 =  70s + 5s graceful =  75s
// SPIKE :  0+10+30+10 =  50s + 5s graceful =  55s
const STD_STAGES = [
  { duration: '10s', target: 10  },
  { duration: '30s', target: 50  },
  { duration: '60s', target: 50  },
  { duration: '10s', target: 0   },
];

const STRESS_STAGES = [
  { duration: '10s', target: 50         },
  { duration: '20s', target: 200        },
  { duration: '30s', target: 200        },
  { duration: '10s', target: 0          },
];

const SPIKE_STAGES = [
  { duration: '10s', target: MAX_SPIKE_VU },  // ani tırmanış
  { duration: '30s', target: MAX_SPIKE_VU },  // pik'i tut
  { duration: '10s', target: 0            },  // ani düşüş
];

const S = 115;  // standart blok
const T = 75;   // stress blok
const K = 55;   // spike blok

export const options = {
  scenarios: {
    // S1: Küçük payload — ürün detay sayfası
    rest_s1_small:   { executor: 'ramping-vus', startTime: `${S*0}s`,       startVUs: 0, stages: STD_STAGES,    gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's1' } },
    grpc_s1_small:   { executor: 'ramping-vus', startTime: `${S*1}s`,       startVUs: 0, stages: STD_STAGES,    gracefulRampDown: '5s', env: { PROTO: 'grpc', SCENE: 's1' } },
    // S2: Büyük payload — katalog sync (~20MB)
    rest_s2_large:   { executor: 'ramping-vus', startTime: `${S*2}s`,       startVUs: 0, stages: STD_STAGES,    gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's2' } },
    grpc_s2_large:   { executor: 'ramping-vus', startTime: `${S*3}s`,       startVUs: 0, stages: STD_STAGES,    gracefulRampDown: '5s', env: { PROTO: 'grpc', SCENE: 's2' } },
    // S3: Yüksek eşzamanlılık — 200 VU sabit yük
    rest_s3_stress:  { executor: 'ramping-vus', startTime: `${S*4}s`,       startVUs: 0, stages: STRESS_STAGES, gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's3' } },
    grpc_s3_stress:  { executor: 'ramping-vus', startTime: `${S*4+T}s`,     startVUs: 0, stages: STRESS_STAGES, gracefulRampDown: '5s', env: { PROTO: 'grpc', SCENE: 's3' } },
    // S4: Flash sale spike — 10 saniyede MAX_SPIKE_VU'ya fırlat
    rest_s4_spike:   { executor: 'ramping-vus', startTime: `${S*4+T*2}s`,   startVUs: 0, stages: SPIKE_STAGES,  gracefulRampDown: '5s', env: { PROTO: 'rest', SCENE: 's4' } },
    grpc_s4_spike:   { executor: 'ramping-vus', startTime: `${S*4+T*2+K}s`, startVUs: 0, stages: SPIKE_STAGES,  gracefulRampDown: '5s', env: { PROTO: 'grpc', SCENE: 's4' } },
  },
  thresholds: {
    rest_s1_ms:        ['p(95)<500'],
    grpc_s1_ms:        ['p(95)<500'],
    rest_s2_ms:        ['p(95)<2000'],
    grpc_s2_ms:        ['p(95)<2000'],
    rest_s3_ms:        ['p(95)<5000'],
    grpc_s3_ms:        ['p(95)<5000'],
    rest_s4_ms:        ['p(95)<10000'],  // spike'ta yüksek latency beklenir
    grpc_s4_ms:        ['p(95)<10000'],
    rest_error_rate:   ['rate<0.10'],    // spike'ta %10'a kadar hata kabul edilebilir
    grpc_error_rate:   ['rate<0.10'],
  },
};

// ── gRPC client (per-VU) ──────────────────────────────────────────────────
const client = new grpc.Client();
client.load(['./proto'], 'product.proto');
let connected = false;

// ── Ana döngü ─────────────────────────────────────────────────────────────
export default function () {
  const proto = __ENV.PROTO;
  const scene = __ENV.SCENE;

  if (proto === 'rest') {
    runRest(scene);
  } else {
    runGrpc(scene);
  }

  sleep(0.1);
}

function runRest(scene) {
  if (scene === 's1') {
    const res = http.get(`${REST_URL}/products/1`);
    restS1.add(res.timings.duration);
    restErrors.add(res.status !== 200);
    check(res, {
      '[S1-REST] status 200':   (r) => r.status === 200,
      '[S1-REST] has id field': (r) => { try { return JSON.parse(r.body).id !== undefined; } catch (e) { return false; } },
    });
  } else if (scene === 's2') {
    const res = http.get(`${REST_URL}/products`);
    restS2.add(res.timings.duration);
    restErrors.add(res.status !== 200);
    check(res, {
      '[S2-REST] status 200':   (r) => r.status === 200,
      '[S2-REST] 1000+ items':  (r) => { try { return JSON.parse(r.body).length >= 1000; } catch (e) { return false; } },
    });
  } else if (scene === 's3') {
    const res = http.get(`${REST_URL}/products`);
    restS3.add(res.timings.duration);
    restErrors.add(res.status !== 200);
    check(res, { '[S3-REST] status 200': (r) => r.status === 200 });
  } else if (scene === 's4') {
    // Spike: tek ürün çek — bağlantı yönetimini izole etmek için hafif endpoint
    const res = http.get(`${REST_URL}/products/1`);
    restS4.add(res.timings.duration);
    restErrors.add(res.status !== 200);
    check(res, { '[S4-REST] status 200': (r) => r.status === 200 });
  }
}

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
    // Spike: tek ürün — bağlantı multiplexing farkını izole etmek için
    const start = Date.now();
    const res = client.invoke('product.ProductGrpcService/GetProduct', { id: 1 });
    grpcS4.add(Date.now() - start);
    grpcErrors.add(res.status !== grpc.StatusOK);
    check(res, { '[S4-gRPC] status OK': (r) => r.status === grpc.StatusOK });
  }
}

// ── Karşılaştırma raporu ──────────────────────────────────────────────────
export function handleSummary(data) {
  const m = data.metrics;

  function get(name, stat) {
    return m[name] && m[name].values[stat] !== undefined ? m[name].values[stat] : null;
  }

  function fmt(val) {
    return val !== null ? val.toFixed(1) + 'ms' : 'N/A     ';
  }

  function delta(rVal, gVal) {
    if (rVal === null || gVal === null) return '—';
    const pct = ((gVal - rVal) / rVal * 100);
    if (Math.abs(pct) < 3) return '≈ eşit';
    return pct > 0
      ? `REST  %${Math.abs(pct).toFixed(0)} daha hızlı`
      : `gRPC  %${Math.abs(pct).toFixed(0)} daha hızlı`;
  }

  function row(label, rm, gm) {
    const rA = get(rm, 'avg'),   gA = get(gm, 'avg');
    const rP = get(rm, 'p(95)'), gP = get(gm, 'p(95)');
    const rM = get(rm, 'max'),   gM = get(gm, 'max');
    return (
      `  ┌─ ${label}\n` +
      `  │  avg    REST: ${fmt(rA).padEnd(10)} gRPC: ${fmt(gA).padEnd(10)} → ${delta(rA, gA)}\n` +
      `  │  p(95)  REST: ${fmt(rP).padEnd(10)} gRPC: ${fmt(gP).padEnd(10)} → ${delta(rP, gP)}\n` +
      `  └─ max    REST: ${fmt(rM).padEnd(10)} gRPC: ${fmt(gM).padEnd(10)} → ${delta(rM, gM)}`
    );
  }

  // Genel ortalama p(95) — 4 senaryonun ortalaması
  const rP95s = ['rest_s1_ms','rest_s2_ms','rest_s3_ms','rest_s4_ms'].map(n => get(n,'p(95)')).filter(v => v !== null);
  const gP95s = ['grpc_s1_ms','grpc_s2_ms','grpc_s3_ms','grpc_s4_ms'].map(n => get(n,'p(95)')).filter(v => v !== null);
  const avgRP95 = rP95s.length ? rP95s.reduce((a,b) => a+b, 0) / rP95s.length : null;
  const avgGP95 = gP95s.length ? gP95s.reduce((a,b) => a+b, 0) / gP95s.length : null;

  const rErr = m['rest_error_rate'] ? (m['rest_error_rate'].values.rate * 100).toFixed(2) : '0.00';
  const gErr = m['grpc_error_rate'] ? (m['grpc_error_rate'].values.rate * 100).toFixed(2) : '0.00';

  const report = `

╔══════════════════════════════════════════════════════════════════╗
║          BENCHMARK KARŞILAŞTIRMA RAPORU — REST vs gRPC           ║
║          Spike MAX VU: ${String(MAX_SPIKE_VU).padEnd(5)}  |  REST hata: %${rErr.padEnd(5)}  gRPC hata: %${gErr.padEnd(5)} ║
╚══════════════════════════════════════════════════════════════════╝

${row('S1 — Küçük Payload      (GetProduct,   1 ürün,        50 VU)', 'rest_s1_ms', 'grpc_s1_ms')}

${row('S2 — Büyük Payload      (ListProducts, ~20MB,         50 VU)', 'rest_s2_ms', 'grpc_s2_ms')}

${row('S3 — Yüksek Eşzamanlılık (ListProducts, ~20MB,       200 VU)', 'rest_s3_ms', 'grpc_s3_ms')}

${row(`S4 — Flash Sale Spike    (GetProduct,   0→${MAX_SPIKE_VU} VU, 10s'de)`, 'rest_s4_ms', 'grpc_s4_ms')}

──────────────────────────────────────────────────────────────────
  GENEL ORTALAMA p(95)  [4 senaryonun ortalaması]
    REST : ${fmt(avgRP95)}
    gRPC : ${fmt(avgGP95)}
    Sonuç: ${delta(avgRP95, avgGP95)}
──────────────────────────────────────────────────────────────────
`;

  return { stdout: report };
}

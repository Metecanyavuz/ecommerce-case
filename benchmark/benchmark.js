import http from 'k6/http';
import grpc from 'k6/net/grpc';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const REST_URL = __ENV.REST_URL || 'http://product-service.railway.internal:8080';
const GRPC_URL = __ENV.GRPC_URL || 'product-service.railway.internal:9090';

// ── Per-scenario latency metrics (her senaryo için ayrı trend) ────────────
const restS1 = new Trend('rest_s1_ms', true);  // S1: 1 ürün
const grpcS1 = new Trend('grpc_s1_ms', true);
const restS2 = new Trend('rest_s2_ms', true);  // S2: 1000 ürün
const grpcS2 = new Trend('grpc_s2_ms', true);
const restS3 = new Trend('rest_s3_ms', true);  // S3: 200 VU stress
const grpcS3 = new Trend('grpc_s3_ms', true);

const errorRate = new Rate('error_rate');

// Senaryo süresi: 10+30+60+10 = 110s + 5s graceful = 115s
// Stress süresi : 10+20+30+10 = 70s  + 5s graceful = 75s
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

const S = 115;  // standart senaryo bloğu (saniye)
const T = 75;   // stress senaryo bloğu (saniye)

export const options = {
  // Senaryolar sırayla çalışır (startTime ile offset verildi)
  scenarios: {
    rest_s1_small: {
      executor: 'ramping-vus', startTime: `${S * 0}s`,
      startVUs: 0, stages: STD_STAGES, gracefulRampDown: '5s',
      env: { PROTO: 'rest', SCENE: 's1' },
    },
    grpc_s1_small: {
      executor: 'ramping-vus', startTime: `${S * 1}s`,
      startVUs: 0, stages: STD_STAGES, gracefulRampDown: '5s',
      env: { PROTO: 'grpc', SCENE: 's1' },
    },
    rest_s2_large: {
      executor: 'ramping-vus', startTime: `${S * 2}s`,
      startVUs: 0, stages: STD_STAGES, gracefulRampDown: '5s',
      env: { PROTO: 'rest', SCENE: 's2' },
    },
    grpc_s2_large: {
      executor: 'ramping-vus', startTime: `${S * 3}s`,
      startVUs: 0, stages: STD_STAGES, gracefulRampDown: '5s',
      env: { PROTO: 'grpc', SCENE: 's2' },
    },
    rest_s3_stress: {
      executor: 'ramping-vus', startTime: `${S * 4}s`,
      startVUs: 0, stages: STRESS_STAGES, gracefulRampDown: '5s',
      env: { PROTO: 'rest', SCENE: 's3' },
    },
    grpc_s3_stress: {
      executor: 'ramping-vus', startTime: `${S * 4 + T}s`,
      startVUs: 0, stages: STRESS_STAGES, gracefulRampDown: '5s',
      env: { PROTO: 'grpc', SCENE: 's3' },
    },
  },
  thresholds: {
    rest_s1_ms:  ['p(95)<500'],
    grpc_s1_ms:  ['p(95)<500'],
    rest_s2_ms:  ['p(95)<2000'],
    grpc_s2_ms:  ['p(95)<2000'],
    rest_s3_ms:  ['p(95)<5000'],
    grpc_s3_ms:  ['p(95)<5000'],
    error_rate:  ['rate<0.05'],
  },
};

// ── gRPC client: module scope = per-VU instance ───────────────────────────
const client = new grpc.Client();
client.load(['./proto'], 'product.proto');
let connected = false;  // per-VU flag — her VU kendi context'inde bir kez bağlanır

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
    errorRate.add(res.status !== 200);
    check(res, {
      '[S1-REST] status 200':   (r) => r.status === 200,
      '[S1-REST] has id field': (r) => { try { return JSON.parse(r.body).id !== undefined; } catch (e) { return false; } },
    });
  } else if (scene === 's2') {
    const res = http.get(`${REST_URL}/products`);
    restS2.add(res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, {
      '[S2-REST] status 200':  (r) => r.status === 200,
      '[S2-REST] 1000+ items': (r) => { try { return JSON.parse(r.body).length >= 1000; } catch (e) { return false; } },
    });
  } else if (scene === 's3') {
    const res = http.get(`${REST_URL}/products`);
    restS3.add(res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, {
      '[S3-REST] status 200': (r) => r.status === 200,
    });
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
    errorRate.add(res.status !== grpc.StatusOK);
    check(res, {
      '[S1-gRPC] status OK': (r) => r.status === grpc.StatusOK,
      '[S1-gRPC] has id':    (r) => r.message && r.message.id !== undefined,
    });
  } else if (scene === 's2') {
    const start = Date.now();
    const res = client.invoke('product.ProductGrpcService/ListProducts', {});
    grpcS2.add(Date.now() - start);
    errorRate.add(res.status !== grpc.StatusOK);
    check(res, {
      '[S2-gRPC] status OK':   (r) => r.status === grpc.StatusOK,
      '[S2-gRPC] 1000+ items': (r) => r.message && r.message.products && r.message.products.length >= 1000,
    });
  } else if (scene === 's3') {
    const start = Date.now();
    const res = client.invoke('product.ProductGrpcService/ListProducts', {});
    grpcS3.add(Date.now() - start);
    errorRate.add(res.status !== grpc.StatusOK);
    check(res, {
      '[S3-gRPC] status OK': (r) => r.status === grpc.StatusOK,
    });
  }
}

// ── Otomatik karşılaştırma raporu ─────────────────────────────────────────
export function handleSummary(data) {
  const m = data.metrics;

  function get(name, stat) {
    return m[name] && m[name].values[stat] !== undefined ? m[name].values[stat] : null;
  }

  function fmt(val) {
    return val !== null ? val.toFixed(1) + 'ms' : 'N/A     ';
  }

  function delta(restVal, grpcVal) {
    if (restVal === null || grpcVal === null) return '';
    const pct = ((grpcVal - restVal) / restVal * 100);
    if (Math.abs(pct) < 3) return '≈ eşit';
    return pct > 0
      ? `REST  %${Math.abs(pct).toFixed(0)} daha hızlı`
      : `gRPC  %${Math.abs(pct).toFixed(0)} daha hızlı`;
  }

  function row(label, rm, gm) {
    const rA = get(rm, 'avg'),      gA = get(gm, 'avg');
    const rP = get(rm, 'p(95)'),    gP = get(gm, 'p(95)');
    const rM = get(rm, 'max'),      gM = get(gm, 'max');
    return (
      `  ┌─ ${label}\n` +
      `  │  avg    REST: ${fmt(rA).padEnd(10)} gRPC: ${fmt(gA).padEnd(10)} → ${delta(rA,  gA)}\n` +
      `  │  p(95)  REST: ${fmt(rP).padEnd(10)} gRPC: ${fmt(gP).padEnd(10)} → ${delta(rP,  gP)}\n` +
      `  └─ max    REST: ${fmt(rM).padEnd(10)} gRPC: ${fmt(gM).padEnd(10)} → ${delta(rM,  gM)}`
    );
  }

  // Genel ortalama p(95)
  const rP95s = ['rest_s1_ms', 'rest_s2_ms', 'rest_s3_ms'].map(n => get(n, 'p(95)')).filter(v => v !== null);
  const gP95s = ['grpc_s1_ms', 'grpc_s2_ms', 'grpc_s3_ms'].map(n => get(n, 'p(95)')).filter(v => v !== null);
  const avgRP95 = rP95s.length ? rP95s.reduce((a, b) => a + b, 0) / rP95s.length : null;
  const avgGP95 = gP95s.length ? gP95s.reduce((a, b) => a + b, 0) / gP95s.length : null;

  const errRate = m['error_rate'] ? (m['error_rate'].values.rate * 100).toFixed(2) : '0.00';

  const report = `

╔══════════════════════════════════════════════════════════════╗
║         BENCHMARK KARŞILAŞTIRMA RAPORU — REST vs gRPC        ║
╠══════════════════════════════════════════════════════════════╣
║  Toplam süre: ~10 dakika  |  6 senaryo  |  Hata: %${errRate.padEnd(5)}       ║
╚══════════════════════════════════════════════════════════════╝

${row('S1 — Küçük Payload   (GetProduct,   1 ürün,   50 VU)', 'rest_s1_ms', 'grpc_s1_ms')}

${row('S2 — Büyük Payload   (ListProducts, 1000 ürün, 50 VU)', 'rest_s2_ms', 'grpc_s2_ms')}

${row('S3 — Yüksek Eşzamanlılık (ListProducts, 1000 ürün, 200 VU)', 'rest_s3_ms', 'grpc_s3_ms')}

──────────────────────────────────────────────────────────────
  GENEL ORTALAMA p(95)  [3 senaryonun ortalaması]
    REST : ${fmt(avgRP95)}
    gRPC : ${fmt(avgGP95)}
    Sonuç: ${delta(avgRP95, avgGP95)}
──────────────────────────────────────────────────────────────
`;

  return { stdout: report };
}

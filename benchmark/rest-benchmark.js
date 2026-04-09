import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL = __ENV.REST_URL || 'http://product-service.railway.internal:8083';

const restLatency   = new Trend('rest_latency_ms', true);
const restErrorRate = new Rate('rest_error_rate');
const restRequests  = new Counter('rest_requests_total');

export const options = {
  scenarios: {
    benchmark: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 10 }, // warm-up
        { duration: '30s', target: 50 }, // ramp-up
        { duration: '60s', target: 50 }, // sustained load
        { duration: '10s', target: 0  }, // ramp-down
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    rest_latency_ms: ['p(95)<1000'],
    rest_error_rate: ['rate<0.01'],
  },
};

export default function () {
  // ── GET /products/1 ──────────────────────────────────────────────────────
  const getRes = http.get(`${BASE_URL}/products/1`);

  restLatency.add(getRes.timings.duration);
  restRequests.add(1);
  restErrorRate.add(getRes.status !== 200);

  check(getRes, {
    'REST GET status 200':      (r) => r.status === 200,
    'REST GET has id field':    (r) => JSON.parse(r.body).id !== undefined,
    'REST GET latency <1000ms': (r) => r.timings.duration < 1000,
  });

  // ── GET /products ─────────────────────────────────────────────────────────
  const listRes = http.get(`${BASE_URL}/products`);

  restLatency.add(listRes.timings.duration);
  restRequests.add(1);
  restErrorRate.add(listRes.status !== 200);

  check(listRes, {
    'REST LIST status 200': (r) => r.status === 200,
    'REST LIST is array':   (r) => Array.isArray(JSON.parse(r.body)),
  });

  sleep(0.1);
}

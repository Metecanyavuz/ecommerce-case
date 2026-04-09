import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL = __ENV.REST_URL || 'http://product-service.railway.internal:8080';

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
    rest_latency_ms: ['p(95)<2000'],
    rest_error_rate: ['rate<0.01'],
  },
};

export default function () {
  // ── GET /products  (large payload — 1000 products, JSON serialization) ────
  const listRes = http.get(`${BASE_URL}/products`);

  restLatency.add(listRes.timings.duration);
  restRequests.add(1);
  restErrorRate.add(listRes.status !== 200);

  check(listRes, {
    'REST LIST status 200':     (r) => r.status === 200,
    'REST LIST is array':       (r) => Array.isArray(JSON.parse(r.body)),
    'REST LIST has 1000+ items': (r) => JSON.parse(r.body).length >= 1000,
  });

  sleep(0.1);
}

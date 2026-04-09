import grpc from 'k6/net/grpc';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const GRPC_URL = __ENV.GRPC_URL || 'product-service.railway.internal:9090';

const grpcLatency   = new Trend('grpc_latency_ms', true);
const grpcErrorRate = new Rate('grpc_error_rate');
const grpcRequests  = new Counter('grpc_requests_total');

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
    grpc_latency_ms: ['p(95)<1000'],
    grpc_error_rate: ['rate<0.01'],
  },
};

// k6 gRPC: her VU kendi bağlantısını default() içinde açmalı
const client = new grpc.Client();
client.load(['./proto'], 'product.proto');

export default function () {
  client.connect(GRPC_URL, { plaintext: true });

  // ── GetProduct ────────────────────────────────────────────────────────────
  const getStart = Date.now();
  const getRes = client.invoke('product.ProductGrpcService/GetProduct', { id: 1 });
  grpcLatency.add(Date.now() - getStart);
  grpcRequests.add(1);
  grpcErrorRate.add(getRes.status !== grpc.StatusOK);

  check(getRes, {
    'gRPC GetProduct OK':     (r) => r.status === grpc.StatusOK,
    'gRPC GetProduct has id': (r) => r.message && r.message.id !== undefined,
  });

  // ── ListProducts ──────────────────────────────────────────────────────────
  const listStart = Date.now();
  const listRes = client.invoke('product.ProductGrpcService/ListProducts', {});
  grpcLatency.add(Date.now() - listStart);
  grpcRequests.add(1);
  grpcErrorRate.add(listRes.status !== grpc.StatusOK);

  check(listRes, {
    'gRPC ListProducts OK':       (r) => r.status === grpc.StatusOK,
    'gRPC ListProducts has data': (r) => r.message && r.message.products !== undefined,
  });

  client.close();
  sleep(0.1);
}

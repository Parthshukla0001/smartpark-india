import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const coldLatency = new Trend('cache_cold_latency', true);
const warmLatency = new Trend('cache_warm_latency', true);
const cacheHits = new Counter('cache_warm_hits');

export const options = {
  scenarios: {
    cache_benchmark: {
      executor: 'shared-iterations',
      vus: 10,
      iterations: 200,
      maxDuration: '30s',
    },
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://backend:8080';

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: 'driver1@smartpark.in', password: 'Password123!' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  const token = JSON.parse(loginRes.body).accessToken;

  // Dynamically resolve a valid active parking lot ID
  let lotId = 1;
  const lotsRes = http.get(`${BASE_URL}/api/parking-lots?size=1`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (lotsRes.status === 200) {
    const lots = JSON.parse(lotsRes.body).content || [];
    if (lots.length > 0) {
      lotId = lots[0].id;
    }
  }

  return { token, lotId };
}

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`,
    'Content-Type': 'application/json',
  };

  const start = Date.now();
  const res = http.get(`${BASE_URL}/api/parking-lots/${data.lotId}`, {
    headers,
    tags: { name: 'GET /api/parking-lots/:id' },
  });
  const duration = Date.now() - start;

  check(res, { 'status is 200': (r) => r.status === 200 });

  if (__ITER === 0 && __VU === 1) {
    coldLatency.add(duration);
  } else {
    warmLatency.add(duration);
    cacheHits.add(1);
  }
}

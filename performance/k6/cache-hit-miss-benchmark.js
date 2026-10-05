import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const reqDuration = new Trend('target_req_duration', true);
const reqCount = new Counter('target_req_count');

export const options = {
  scenarios: {
    cache_stress: {
      executor: 'shared-iterations',
      vus: 20,
      iterations: 2000,
      maxDuration: '30s',
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
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

  const lotsRes = http.get(`${BASE_URL}/api/parking-lots?size=1`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  let lotId = 1;
  if (lotsRes.status === 200) {
    const lots = JSON.parse(lotsRes.body).content || [];
    if (lots.length > 0) lotId = lots[0].id;
  }

  // Prime cache with 1 query
  http.get(`${BASE_URL}/api/parking-lots/${lotId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });

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

  reqDuration.add(duration);
  reqCount.add(1);

  check(res, { 'status is 200': (r) => r.status === 200 });
}

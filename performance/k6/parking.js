import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const searchDuration = new Trend('smartpark_parking_search_duration', true);
const lotDetailsDuration = new Trend('smartpark_lot_details_duration', true);
const slotsDuration = new Trend('smartpark_slots_duration', true);
const parkingSuccessRate = new Rate('smartpark_parking_success_rate');
const parkingErrors = new Counter('smartpark_parking_errors');

export const options = {
  scenarios: {
    parking_discovery: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '10s', target: 10 },
        { duration: '20s', target: 25 },
        { duration: '20s', target: 50 },
        { duration: '20s', target: 100 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'smartpark_parking_success_rate': ['rate>0.99'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://backend:8080';
const CITIES = ['Bhubaneswar', 'Bengaluru', 'Mumbai', 'Hyderabad', 'Pune'];

export function setup() {
  // Obtain tokens for 50 driver accounts to distribute rate-limit quotas evenly
  const tokens = [];
  for (let i = 1; i <= 50; i++) {
    const res = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: `driver${i}@smartpark.in`, password: 'Password123!' }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    if (res.status === 200) {
      tokens.push(JSON.parse(res.body).accessToken);
    }
  }

  // Pre-fetch parking lots list that have active floors
  const lotsRes = http.get(`${BASE_URL}/api/parking-lots?size=50`, {
    headers: { Authorization: `Bearer ${tokens[0]}` },
  });
  let lotIds = [];
  if (lotsRes.status === 200) {
    const content = JSON.parse(lotsRes.body).content || [];
    for (const lot of content) {
      const floorsRes = http.get(`${BASE_URL}/api/parking-lots/${lot.id}/floors`, {
        headers: { Authorization: `Bearer ${tokens[0]}` },
      });
      if (floorsRes.status === 200) {
        const floors = JSON.parse(floorsRes.body);
        if (Array.isArray(floors) && floors.length > 0) {
          lotIds.push(lot.id);
        }
      }
    }
  }
  if (lotIds.length === 0) {
    lotIds = [1];
  }

  return { tokens, lotIds };
}

export default function (data) {
  const token = data.tokens[__VU % data.tokens.length];
  const lotId = data.lotIds[(__VU + __ITER) % data.lotIds.length];
  const city = CITIES[__ITER % CITIES.length];

  const authParams = {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  };

  // 1. Search parking lots by city (tests lower(city) index + Redis cache)
  let start = Date.now();
  let res = http.get(`${BASE_URL}/api/parking-lots?city=${city}&size=10`, {
    ...authParams,
    tags: { name: 'GET /api/parking-lots?city' },
  });
  searchDuration.add(Date.now() - start);

  let ok = check(res, {
    'search lots status 200': (r) => r.status === 200,
  });
  parkingSuccessRate.add(ok);
  if (!ok) parkingErrors.add(1);

  // 2. Fetch specific parking lot details (tests Redis cache-aside)
  start = Date.now();
  res = http.get(`${BASE_URL}/api/parking-lots/${lotId}`, {
    ...authParams,
    tags: { name: 'GET /api/parking-lots/:id' },
  });
  lotDetailsDuration.add(Date.now() - start);

  ok = check(res, {
    'lot details status 200': (r) => r.status === 200,
  });
  parkingSuccessRate.add(ok);
  if (!ok) parkingErrors.add(1);

  // 3. Fetch floors for lot
  res = http.get(`${BASE_URL}/api/parking-lots/${lotId}/floors`, {
    ...authParams,
    tags: { name: 'GET /api/parking-lots/:id/floors' },
  });

  ok = check(res, {
    'lot floors status 200': (r) => r.status === 200,
  });
  parkingSuccessRate.add(ok);
  if (!ok) parkingErrors.add(1);

  let floorId = null;
  if (res.status === 200) {
    try {
      const floors = JSON.parse(res.body);
      if (Array.isArray(floors) && floors.length > 0) {
        floorId = floors[0].id;
      }
    } catch (e) {}
  }

  // 4. Fetch slots for floor (if floor exists)
  if (floorId) {
    start = Date.now();
    res = http.get(`${BASE_URL}/api/parking-lots/${lotId}/floors/${floorId}/slots`, {
      ...authParams,
      tags: { name: 'GET /api/parking-lots/:id/floors/:id/slots' },
    });
    slotsDuration.add(Date.now() - start);

    ok = check(res, {
      'floor slots status 200': (r) => r.status === 200,
    });
    parkingSuccessRate.add(ok);
    if (!ok) parkingErrors.add(1);
  }

  sleep(0.05);
}

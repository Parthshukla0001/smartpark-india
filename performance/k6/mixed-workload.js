import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// Custom latency trends by transaction type
const readLatency = new Trend('workload_read_latency', true);
const writeLatency = new Trend('workload_write_latency', true);
const overallSuccessRate = new Rate('workload_success_rate');
const systemErrors = new Counter('workload_system_errors');
const expectedConflicts = new Counter('workload_expected_conflicts');

export const options = {
  scenarios: {
    staged_load: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        // Level 1: 10 VUs
        { duration: '15s', target: 10 },
        // Level 2: 25 VUs
        { duration: '20s', target: 25 },
        // Level 3: 50 VUs
        { duration: '20s', target: 50 },
        // Level 4: 100 VUs
        { duration: '20s', target: 100 },
        // Ramp down
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.02'],
    'workload_success_rate': ['rate>0.98'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://backend:8080';
const CITIES = ['Bhubaneswar', 'Bengaluru', 'Mumbai', 'Hyderabad', 'Pune'];

export function setup() {
  const drivers = [];
  for (let i = 1; i <= 50; i++) {
    const loginRes = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: `driver${i}@smartpark.in`, password: 'Password123!' }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    if (loginRes.status === 200) {
      const token = JSON.parse(loginRes.body).accessToken;
      const vehRes = http.get(`${BASE_URL}/api/vehicles`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      let vehicleId = null;
      if (vehRes.status === 200) {
        const vehicles = JSON.parse(vehRes.body);
        if (vehicles.length > 0) {
          vehicleId = vehicles[0].id;
        }
      }
      drivers.push({ token, vehicleId, email: `driver${i}@smartpark.in` });
    }
  }

  if (drivers.length === 0) {
    throw new Error('Setup failed: No drivers could be authenticated!');
  }

  // Pre-fetch lots that have active floors and slots
  const lotsRes = http.get(`${BASE_URL}/api/parking-lots?size=50`, {
    headers: { Authorization: `Bearer ${drivers[0].token}` },
  });
  let lotIds = [];
  let slotIds = [];
  if (lotsRes.status === 200) {
    const content = JSON.parse(lotsRes.body).content || [];
    for (const lot of content) {
      const floorsRes = http.get(`${BASE_URL}/api/parking-lots/${lot.id}/floors`, {
        headers: { Authorization: `Bearer ${drivers[0].token}` },
      });
      if (floorsRes.status === 200) {
        const floors = JSON.parse(floorsRes.body);
        if (Array.isArray(floors) && floors.length > 0) {
          lotIds.push(lot.id);
          for (const floor of floors) {
            const slotsRes = http.get(`${BASE_URL}/api/parking-lots/${lot.id}/floors/${floor.id}/slots`, {
              headers: { Authorization: `Bearer ${drivers[0].token}` },
            });
            if (slotsRes.status === 200) {
              const slots = JSON.parse(slotsRes.body);
              for (const s of slots) {
                slotIds.push(s.id);
              }
            }
            if (slotIds.length >= 200) break;
          }
        }
      }
      if (slotIds.length >= 200 && lotIds.length >= 10) break;
    }
  }

  return { drivers, lotIds, slotIds };
}

export default function (data) {
  if (!data.drivers || data.drivers.length === 0) return;

  const driverIndex = (__VU + __ITER) % data.drivers.length;
  const driver = data.drivers[driverIndex];
  const authHeaders = {
    Authorization: `Bearer ${driver.token}`,
    'Content-Type': 'application/json',
  };

  // Determine transaction type based on realistic operational probability:
  // 60% Search & Details (Read)
  // 20% History & Notifications (Read)
  // 15% Booking Lifecycle (Write: Create / Cancel)
  // 5%  Authentication Login (Auth)
  const roll = Math.random();

  if (roll < 0.60) {
    // A. Parking Discovery (Read)
    const city = CITIES[__ITER % CITIES.length];
    const lotId = data.lotIds[(__VU + __ITER) % data.lotIds.length];

    const start = Date.now();
    const res = http.get(`${BASE_URL}/api/parking-lots?city=${city}&size=10`, {
      headers: authHeaders,
      tags: { name: 'GET /api/parking-lots' },
    });
    readLatency.add(Date.now() - start);

    const ok = check(res, { 'get lots status 200': (r) => r.status === 200 });
    overallSuccessRate.add(ok);
    if (!ok) systemErrors.add(1);

    // Follow-up with lot details (cache-aside read)
    const resDetail = http.get(`${BASE_URL}/api/parking-lots/${lotId}`, {
      headers: authHeaders,
      tags: { name: 'GET /api/parking-lots/:id' },
    });
    check(resDetail, { 'get lot detail status 200': (r) => r.status === 200 });

  } else if (roll < 0.80) {
    // B. Booking History & Notifications (Read)
    const start = Date.now();
    const histRes = http.get(`${BASE_URL}/api/bookings?page=0&size=10`, {
      headers: authHeaders,
      tags: { name: 'GET /api/bookings' },
    });
    readLatency.add(Date.now() - start);

    const histOk = check(histRes, { 'get history status 200': (r) => r.status === 200 });
    overallSuccessRate.add(histOk);
    if (!histOk) systemErrors.add(1);

    // Query notifications
    const notifRes = http.get(`${BASE_URL}/api/notifications`, {
      headers: authHeaders,
      tags: { name: 'GET /api/notifications' },
    });
    check(notifRes, { 'get notif status 200': (r) => r.status === 200 });

  } else if (roll < 0.95) {
    // C. Booking Lifecycle (Write)
    if (driver.vehicleId) {
      const slotId = data.slotIds[(__VU * 3 + __ITER) % data.slotIds.length];
      const targetDate = new Date();
      targetDate.setUTCDate(targetDate.getUTCDate() + 5 + ((__ITER + __VU) % 15));
      targetDate.setUTCHours(10, 0, 0, 0);
      const startTime = targetDate.toISOString();
      targetDate.setUTCHours(11, 0, 0, 0);
      const endTime = targetDate.toISOString();

      const payload = JSON.stringify({
        vehicleId: driver.vehicleId,
        parkingSlotId: slotId,
        startTime,
        endTime,
      });

      const start = Date.now();
      const createRes = http.post(`${BASE_URL}/api/bookings`, payload, {
        headers: authHeaders,
        tags: { name: 'POST /api/bookings' },
      });
      writeLatency.add(Date.now() - start);

      if (createRes.status === 200 || createRes.status === 201) {
        overallSuccessRate.add(1);
        try {
          const created = JSON.parse(createRes.body);
          if (created.id) {
            // Cancel booking to release slot and test cancellation path
            http.post(`${BASE_URL}/api/bookings/${created.id}/cancel`, null, {
              headers: authHeaders,
              tags: { name: 'POST /api/bookings/:id/cancel' },
            });
          }
        } catch (e) {}
      } else if (createRes.status === 409) {
        // Legitimate business conflict
        expectedConflicts.add(1);
        overallSuccessRate.add(1);
      } else {
        systemErrors.add(1);
        overallSuccessRate.add(0);
      }
    }
  } else {
    // D. Auth Login (5% of requests)
    const userIndex = (__VU % 50) + 1;
    const loginPayload = JSON.stringify({
      email: `driver${userIndex}@smartpark.in`,
      password: 'Password123!',
    });

    const start = Date.now();
    const loginRes = http.post(`${BASE_URL}/api/auth/login`, loginPayload, {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /api/auth/login' },
    });
    writeLatency.add(Date.now() - start);

    const ok = check(loginRes, { 'login status 200': (r) => r.status === 200 });
    overallSuccessRate.add(ok);
    if (!ok) systemErrors.add(1);
  }

  sleep(0.05);
}

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const successfulBookings = new Counter('concurrency_successful_bookings');
const conflictBookings = new Counter('concurrency_conflict_bookings');
const unexpectedErrors = new Counter('concurrency_unexpected_errors');
const contentionDuration = new Trend('concurrency_request_duration', true);

export const options = {
  scenarios: {
    slot_contention_race: {
      executor: 'per-vu-iterations',
      vus: 50,
      iterations: 1,
      maxDuration: '30s',
    },
  },
  thresholds: {
    'concurrency_successful_bookings': ['count==1'],
    'concurrency_conflict_bookings': ['count==49'],
    'concurrency_unexpected_errors': ['count==0'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://backend:8080';

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
      if (vehRes.status === 200) {
        const vehicles = JSON.parse(vehRes.body);
        if (vehicles.length > 0) {
          drivers.push({ token, vehicleId: vehicles[0].id });
        }
      }
    }
  }

  // Dynamically resolve target slot from an active lot with floors & slots
  let targetSlotId = null;
  const lotsRes = http.get(`${BASE_URL}/api/parking-lots?city=Bhubaneswar&size=50`, {
    headers: { Authorization: `Bearer ${drivers[0].token}` },
  });
  if (lotsRes.status === 200) {
    const lots = JSON.parse(lotsRes.body).content || [];
    for (const lot of lots) {
      const floorsRes = http.get(`${BASE_URL}/api/parking-lots/${lot.id}/floors`, {
        headers: { Authorization: `Bearer ${drivers[0].token}` },
      });
      if (floorsRes.status === 200) {
        const floors = JSON.parse(floorsRes.body);
        if (floors.length > 0) {
          const floorId = floors[0].id;
          const slotsRes = http.get(`${BASE_URL}/api/parking-lots/${lot.id}/floors/${floorId}/slots`, {
            headers: { Authorization: `Bearer ${drivers[0].token}` },
          });
          if (slotsRes.status === 200) {
            const slots = JSON.parse(slotsRes.body);
            if (slots.length > 0) {
              targetSlotId = slots[slots.length - 1].id;
              console.log(`[SETUP] Resolved target slot ID=${targetSlotId} on lot ${lot.name}`);
              break;
            }
          }
        }
      }
    }
  }

  // Define identical overlapping time window for all 50 VUs (within operating hours 06:00 - 23:00)
  const futureDays = 25 + (Math.floor(Date.now() / 1000) % 30);
  const targetDate = new Date();
  targetDate.setUTCDate(targetDate.getUTCDate() + futureDays);
  targetDate.setUTCHours(10, 0, 0, 0);
  const startTime = targetDate.toISOString();
  targetDate.setUTCHours(12, 0, 0, 0);
  const endTime = targetDate.toISOString();

  console.log(`[SETUP] Slot Contention Target: Slot ID=${targetSlotId}, Interval=[${startTime} - ${endTime}]`);
  return { drivers, targetSlotId, startTime, endTime };
}

export default function (data) {
  const driver = data.drivers[__VU - 1] || data.drivers[0];
  const payload = JSON.stringify({
    vehicleId: driver.vehicleId,
    parkingSlotId: data.targetSlotId,
    startTime: data.startTime,
    endTime: data.endTime,
  });

  const headers = {
    Authorization: `Bearer ${driver.token}`,
    'Content-Type': 'application/json',
  };

  const start = Date.now();
  const res = http.post(`${BASE_URL}/api/bookings`, payload, {
    headers,
    tags: { name: 'RACE_CONDITION_BURST' },
  });
  contentionDuration.add(Date.now() - start);

  if (res.status === 200 || res.status === 201) {
    successfulBookings.add(1);
    check(res, { 'status is 200/201 (winner)': (r) => r.status === 200 || r.status === 201 });
  } else if (res.status === 409) {
    conflictBookings.add(1);
    check(res, { 'status is 409 (expected business conflict)': (r) => r.status === 409 });
  } else {
    unexpectedErrors.add(1);
    console.log(`[CONCURRENCY_ERROR] VU ${__VU} got status ${res.status}: ${res.body}`);
    check(res, { 'unexpected status': () => false });
  }
}

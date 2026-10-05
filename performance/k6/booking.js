import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const bookingCreateDuration = new Trend('smartpark_booking_create_duration', true);
const bookingHistoryDuration = new Trend('smartpark_booking_history_duration', true);
const bookingCancelDuration = new Trend('smartpark_booking_cancel_duration', true);
const bookingSuccessRate = new Rate('smartpark_booking_success_rate');
const bookingErrors = new Counter('smartpark_booking_errors');

export const options = {
  scenarios: {
    booking_lifecycle: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '10s', target: 10 },
        { duration: '20s', target: 25 },
        { duration: '20s', target: 50 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.02'],
    'smartpark_booking_success_rate': ['rate>0.98'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://backend:8080';

export function setup() {
  const driverData = [];

  // Authenticate 50 drivers and retrieve their assigned vehicles
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
        if (Array.isArray(vehicles) && vehicles.length > 0) {
          vehicleId = vehicles[0].id;
        }
      }
      if (vehicleId) {
        driverData.push({ token, vehicleId });
      }
    }
  }

  if (driverData.length === 0) {
    throw new Error('Setup failed: No drivers could be authenticated!');
  }

  // Pre-fetch available slot IDs across active benchmark lots and floors
  const lotsRes = http.get(`${BASE_URL}/api/parking-lots?size=50`, {
    headers: { Authorization: `Bearer ${driverData[0].token}` },
  });
  let slotIds = [];
  if (lotsRes.status === 200) {
    const lots = JSON.parse(lotsRes.body).content || [];
    for (const lot of lots) {
      const floorsRes = http.get(`${BASE_URL}/api/parking-lots/${lot.id}/floors`, {
        headers: { Authorization: `Bearer ${driverData[0].token}` },
      });
      if (floorsRes.status === 200) {
        const floors = JSON.parse(floorsRes.body);
        for (const floor of floors) {
          const slotsRes = http.get(`${BASE_URL}/api/parking-lots/${lot.id}/floors/${floor.id}/slots`, {
            headers: { Authorization: `Bearer ${driverData[0].token}` },
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
      if (slotIds.length >= 200) break;
    }
  }

  return { driverData, slotIds };
}

export default function (data) {
  if (!data.driverData || data.driverData.length === 0) return;

  const driverIndex = (__VU + __ITER) % data.driverData.length;
  const driver = data.driverData[driverIndex];
  const authHeaders = {
    Authorization: `Bearer ${driver.token}`,
    'Content-Type': 'application/json',
  };

  // 1. Query booking history (tests idx_bookings_user_start_time and pagination)
  let start = Date.now();
  let res = http.get(`${BASE_URL}/api/bookings?page=0&size=10`, {
    headers: authHeaders,
    tags: { name: 'GET /api/bookings' },
  });
  bookingHistoryDuration.add(Date.now() - start);

  let ok = check(res, {
    'history status 200': (r) => r.status === 200,
  });
  bookingSuccessRate.add(ok);
  if (!ok) bookingErrors.add(1);

  // 2. Create a new booking
  // Pick slot and generate time strictly within parking facility operating hours (06:00 - 23:00)
  const slotId = data.slotIds[(__VU + __ITER) % data.slotIds.length];
  const targetDate = new Date();
  targetDate.setUTCDate(targetDate.getUTCDate() + 5 + ((__ITER + __VU) % 15));
  targetDate.setUTCHours(10, 0, 0, 0);
  const startTime = targetDate.toISOString();
  targetDate.setUTCHours(11, 0, 0, 0);
  const endTime = targetDate.toISOString();

  const bookingPayload = JSON.stringify({
    vehicleId: driver.vehicleId,
    parkingSlotId: slotId,
    startTime: startTime,
    endTime: endTime,
  });

  start = Date.now();
  res = http.post(`${BASE_URL}/api/bookings`, bookingPayload, {
    headers: authHeaders,
    tags: { name: 'POST /api/bookings' },
  });
  bookingCreateDuration.add(Date.now() - start);

  let bookingId = null;
  const isCreated = res.status === 200 || res.status === 201;
  const isConflict = res.status === 409;

  if (!isCreated && !isConflict) {
    console.log(`[BOOKING_CREATE_FAIL] status=${res.status} body=${res.body}`);
  }

  ok = check(res, {
    'create booking status 200/201 or 409 conflict': (r) => r.status === 200 || r.status === 201 || r.status === 409,
  });
  bookingSuccessRate.add(ok);
  if (!ok) bookingErrors.add(1);

  if (isCreated) {
    try {
      const created = JSON.parse(res.body);
      bookingId = created.id;
    } catch (e) {}

    // 3. Cancel the newly created booking (tests booking lifecycle cancellation & state transition)
    if (bookingId) {
      start = Date.now();
      const cancelRes = http.post(`${BASE_URL}/api/bookings/${bookingId}/cancel`, null, {
        headers: authHeaders,
        tags: { name: 'POST /api/bookings/:id/cancel' },
      });
      bookingCancelDuration.add(Date.now() - start);

      const cancelOk = check(cancelRes, {
        'cancel booking status 200': (r) => r.status === 200,
      });
      bookingSuccessRate.add(cancelOk);
      if (!cancelOk) bookingErrors.add(1);
    }
  }

  sleep(0.1);
}

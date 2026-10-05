import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const loginDuration = new Trend('smartpark_login_duration', true);
const loginSuccessRate = new Rate('smartpark_login_success_rate');
const loginErrors = new Counter('smartpark_login_errors');

export const options = {
  scenarios: {
    auth_ramp: {
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
    'http_req_failed': ['rate<0.01'],
    'smartpark_login_success_rate': ['rate>0.99'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://backend:8080';

export default function () {
  // Rotate through 100 benchmark driver accounts
  const userId = (__VU % 100) + 1;
  const email = `driver${userId}@smartpark.in`;
  const password = 'Password123!';

  const payload = JSON.stringify({ email, password });
  const params = {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST /api/auth/login' },
  };

  const start = Date.now();
  const res = http.post(`${BASE_URL}/api/auth/login`, payload, params);
  const duration = Date.now() - start;

  loginDuration.add(duration);

  const success = check(res, {
    'login status is 200': (r) => r.status === 200,
    'login has token': (r) => {
      try {
        const json = JSON.parse(r.body);
        return json.accessToken !== undefined && json.accessToken.length > 20;
      } catch (e) {
        return false;
      }
    },
  });

  loginSuccessRate.add(success);
  if (!success) {
    loginErrors.add(1);
  }

  // Small pacing think time (50ms - 150ms) to model real user keystrokes / form submissions
  sleep(0.1);
}

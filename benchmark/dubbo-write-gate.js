import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://consumer:8080').replace(/\/$/, '');
const mode = (__ENV.MODE || 'mixed').toLowerCase();
const maxErrorRate = Number(__ENV.MAX_ERROR_RATE || 0);
const p99Ms = Number(__ENV.P99_MS || 250);
const failures = new Counter('write_failures');
const rawLatency = new Trend('raw_write_latency', true);
const typedLatency = new Trend('typed_write_latency', true);

export const options = {
  vus: Number(__ENV.VUS || 8),
  duration: __ENV.DURATION || '30s',
  discardResponseBodies: false,
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: [`rate<=${maxErrorRate}`],
    http_req_duration: [`p(99)<${p99Ms}`],
  },
};

function invoke(typed) {
  const unique = `${Date.now()}-${__VU}-${__ITER}`;
  const body = JSON.stringify({
    customerNo: `GATE-${unique}`,
    fullName: `Write Gate Customer ${unique}`,
    segment: ['pilot', 'enterprise', 'standard'][__ITER % 3],
    email: `gate-${unique}@example.com`,
    requestId: `gate-${unique}`,
  });
  const endpoint = typed ? 'typed' : 'raw';
  const path = typed ? '/api/v1/customers/typed' : '/api/v1/customers';
  const response = http.post(`${baseUrl}${path}`, body, {
    timeout: __ENV.REQUEST_TIMEOUT || '5s',
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint },
  });

  (typed ? typedLatency : rawLatency).add(response.timings.duration);
  if (response.status !== 201) {
    failures.add(1, { endpoint, status: `${response.status}` });
  }
  check(response, { 'write status is 201': (result) => result.status === 201 });
}

export default function () {
  if (mode === 'raw') {
    invoke(false);
  } else if (mode === 'typed') {
    invoke(true);
  } else {
    invoke((__ITER & 1) !== 0);
  }
}

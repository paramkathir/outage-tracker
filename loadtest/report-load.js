// k6 load test: https://k6.io  (brew install k6 / choco install k6)
//
// Start the app with rate limiting and duplicate suppression off, since all traffic comes from one IP:
//   mvn spring-boot:run -Dspring-boot.run.arguments="--tracker.rate-limit.enabled=false --tracker.reports.duplicate-window=0s --tracker.demo.seed-enabled=false"
// Then:
//   k6 run loadtest/report-load.js
//
// Reports are spread over 20 "neighbourhoods" around DFW so the clustering path
// (attach / open incident / leave unclustered) is exercised realistically.

import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        reports: {
            executor: 'constant-arrival-rate',
            rate: 500,            // requests per second
            timeUnit: '1s',
            duration: '60s',
            preAllocatedVUs: 100,
            maxVUs: 400,
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<100', 'p(99)<250'],
    },
};

const SERVICES = ['INTERNET', 'TV', 'PHONE', 'MOBILE'];
const HOTSPOTS = Array.from({ length: 20 }, (_, i) => [32.6 + (i % 5) * 0.12, -97.3 + Math.floor(i / 5) * 0.2]);

export default function () {
    const [lat, lng] = HOTSPOTS[Math.floor(Math.random() * HOTSPOTS.length)];
    const body = JSON.stringify({
        serviceType: SERVICES[Math.floor(Math.random() * SERVICES.length)],
        latitude: lat + (Math.random() - 0.5) * 0.02,
        longitude: lng + (Math.random() - 0.5) * 0.02,
    });
    const res = http.post('http://localhost:8080/api/reports', body, {
        headers: { 'Content-Type': 'application/json' },
    });
    check(res, { 'status is 201': (r) => r.status === 201 });
}

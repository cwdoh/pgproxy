import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const errorCounter = new Counter('errors');

const generateUuidV4 = () => {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0,
            v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

export const options = {
    stages: [
        { duration: '30s', target: 2000 },
        { duration: '1m', target: 2000 },
        { duration: '30s', target: 0 },
    ],

    thresholds: {
        'http_req_duration': ['p(95) < 500'],
        'http_req_failed': ['rate < 0.01'],
    },
};

const payload = JSON.stringify({
    "id": generateUuidV4(),
    "amount_cents": 1000
});

const params = {
    headers: {
        'Content-Type': 'application/json',
    },
};

export default function () {
    const url = 'http://localhost:8081/payments';

    const res = http.post(url, payload, params);

    // Check if the response status is NOT a success (e.g., >= 400)
    if (res.status >= 400) {
        const errorTag = `status-${res.status}`;

        check(res, {
            'is success status': (r) => r.status >= 200 && r.status < 400,
        }, { status: errorTag }); // Apply the status tag to the check itself
    }

    const success = check(res, {
        'is status 200 or 202': (r) => r.status === 200 || r.status === 202,
    });

    if (!success) {
        errorCounter.add(1);
    }

    sleep(Math.random() * 1 + 0.5);
}

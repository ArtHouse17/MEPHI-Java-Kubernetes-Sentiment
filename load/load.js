import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: Number(__ENV.VUS || '30'),
    duration: '5m',
    thresholds: {
        checks: ['rate>0.99'],
    },
};

const payload = encodeURIComponent('good excellent amazing '.repeat(120).trim());
const url = 'http://sentiment.sentiment.svc.cluster.local:8080/api/sentiment?text=' + payload;

export default function () {
    const response = http.get(url, { tags: { name: 'GET /api/sentiment' } });
    check(response, {
        'HTTP 200 and positive': (r) => r.status === 200 && r.json('sentiment') === 'positive',
    });
}

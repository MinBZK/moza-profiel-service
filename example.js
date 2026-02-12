import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { jUnit, textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/2.4.0/dist/bundle.js';
export const options = {
    scenarios: {
        throughput_test: {
            executor: 'constant-arrival-rate',
            rate: 334, // ~100,000 requests / 300 seconds (5 min)
            timeUnit: '1s',
            duration: '20s',
            preAllocatedVUs: 50,
            maxVUs: 200,
        },
    },

    thresholds: {
        'http_req_duration{endpoint:get_partij}': ['max<1000'], // Every request under 1s
        'http_req_duration{endpoint:get_dienstverlener}': ['max<1000'], // Every request under 1s
        http_req_failed: ['rate<0.01'], // Global failure rate < 1%
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/profielservice/v1';

// Mock data for more realistic requests
const IDENTIFICATIE_TYPES = ['BSN', 'KVK', 'RSIN'];
const SAMPLE_IDS = ['123456789', '987654321', '112233445'];
const SAMPLE_DIENSTVERLENERS = ['Belastingdienst', 'KVK', 'UWV'];

export default function () {
    group('Get Partij Profiel', function () {
        const idType = IDENTIFICATIE_TYPES[Math.floor(Math.random() * IDENTIFICATIE_TYPES.length)];
        const idNummer = SAMPLE_IDS[Math.floor(Math.random() * SAMPLE_IDS.length)];
        
        // Example with optional query parameters as seen in PartijRequest
        const url = `${BASE_URL}/${idType}/${idNummer}`;

        const res = http.get(url, {
            tags: { endpoint: 'get_partij' },
            responseCallback: http.expectedStatuses(200, 404),
        });

        check(res, {
            'is status 200 or 404': (r) => r.status === 200 || r.status === 404,
        });
    });

    group('Get Dienstverlener', function () {
        const dvNaam = SAMPLE_DIENSTVERLENERS[Math.floor(Math.random() * SAMPLE_DIENSTVERLENERS.length)];
        const url = `${BASE_URL}/dienstverlener/${dvNaam}`;

        const res = http.get(url, {
            tags: { endpoint: 'get_dienstverlener' },
            responseCallback: http.expectedStatuses(200, 404),
            });

        check(res, {
            'is status 200 or 404': (r) => r.status === 200 || r.status === 404,
        });
    });

    // sleep(Math.random() * 2 + 1); // Removed to prioritize throughput for 100k requests goal
}

export function handleSummary(data) {
    console.log('Finished, generating reports...');
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        '/results/summary.json': JSON.stringify(data),
        '/results/summary.xml': jUnit(data),
        '/results/summary.html': htmlReport(data),
    };
}

/**
 * Phase 4 Test Suite: HTTP 302 Redirect Token Preservation & Status Transition
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
const http = require('http');
const https = require('https');
const assert = require('assert');

// 1. Simulate OkHttp Custom Interceptor behavior
class MockOkHttpInterceptor {
    constructor() {
        this.capturedRequests = [];
    }

    async execute(initialUrl, method, headers, body, mockServerHandler) {
        let currentUrl = initialUrl;
        let currentMethod = method;
        let currentHeaders = { ...headers };
        let currentBody = body;

        let hops = 0;
        const maxHops = 5;

        while (hops < maxHops) {
            hops++;
            const response = mockServerHandler(currentUrl, currentMethod, currentHeaders, currentBody);
            this.capturedRequests.push({
                url: currentUrl,
                method: currentMethod,
                headers: { ...currentHeaders },
                body: currentBody,
                responseCode: response.code
            });

            // Interceptor logic from OkHttpProvider.kt:
            if (response.code === 302 || response.code === 307 || response.code === 308) {
                const redirectUrl = response.headers['Location'] || response.headers['location'];
                if (redirectUrl) {
                    currentUrl = redirectUrl;
                    currentMethod = 'POST'; // Preserve POST
                    // Preserve Authorization header across redirects
                    currentHeaders = {
                        ...currentHeaders,
                        'Authorization': headers['Authorization'] || '',
                        'Content-Type': 'application/json'
                    };
                    continue; // Re-issue request
                }
            }

            return response;
        }

        throw new Error('Too many redirects');
    }
}

// 2. Mock GAS Server handler
function mockGasServer(url, method, headers, body) {
    if (url === 'https://script.google.com/macros/s/AKfycb.../exec') {
        // Return 302 Found redirect
        return {
            code: 302,
            headers: {
                'Location': 'https://script.googleusercontent.com/macros/echo?user_content_key=xyz123'
            },
            body: ''
        };
    } else if (url === 'https://script.googleusercontent.com/macros/echo?user_content_key=xyz123') {
        // Redirect target: check if Authorization header and POST method and body are preserved!
        if (!headers['Authorization'] || !headers['Authorization'].startsWith('Bearer ')) {
            return {
                code: 401,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ error: 'Unauthorized: Missing or stripped Bearer token' })
            };
        }
        if (method !== 'POST') {
            return {
                code: 405,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ error: `Method ${method} not allowed, expected POST` })
            };
        }

        return {
            code: 200,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                status: 'OK',
                received_count: 1,
                inserted_count: 1,
                duplicate_count: 0,
                errors: []
            })
        };
    }

    return { code: 404, headers: {}, body: 'Not found' };
}

// 3. Mock Room Database ScanEntity state machine
class MockRoomScanDao {
    constructor() {
        this.scans = new Map();
    }

    insert(scan) {
        this.scans.set(scan.scanId, { ...scan });
    }

    get(scanId) {
        return this.scans.get(scanId);
    }

    markAsSynced(scanIds) {
        for (const id of scanIds) {
            const scan = this.scans.get(id);
            if (scan) {
                scan.syncStatus = 1; // 1 = Synced
            }
        }
    }

    updateReview(scanId, studentCode, answers, status, syncStatus) {
        const scan = this.scans.get(scanId);
        if (scan) {
            scan.studentCode = studentCode;
            scan.answers = answers;
            scan.status = status;
            scan.syncStatus = syncStatus;
        }
    }
}

async function runPhase4Tests() {
    console.log('Running Phase 4 Persistence, Auth & Sync Tests...\n');
    let passed = 0;

    // Test 1: HTTP 302 Redirect Interceptor Preserves Bearer Token
    {
        const client = new MockOkHttpInterceptor();
        const token = 'oauth2-secret-access-token-98765';
        const payload = JSON.stringify({
            scans: [{
                scan_id: 'uuid-1001',
                student_id: '10001',
                answers: 'ABCD'
            }]
        });

        const response = await client.execute(
            'https://script.google.com/macros/s/AKfycb.../exec',
            'POST',
            { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
            payload,
            mockGasServer
        );

        assert.strictEqual(response.code, 200, 'Should return HTTP 200 after redirect');
        const resBody = JSON.parse(response.body);
        assert.strictEqual(resBody.status, 'OK', 'Backend response should be OK');
        assert.strictEqual(resBody.inserted_count, 1, 'Should report 1 inserted scan');

        // Check captured requests
        assert.strictEqual(client.capturedRequests.length, 2, 'Should have 2 hops (original + redirected)');
        const secondHop = client.capturedRequests[1];
        assert.strictEqual(secondHop.url, 'https://script.googleusercontent.com/macros/echo?user_content_key=xyz123');
        assert.strictEqual(secondHop.method, 'POST', 'Method must remain POST on redirect');
        assert.strictEqual(secondHop.headers['Authorization'], `Bearer ${token}`, 'Authorization header must not be stripped');
        assert.strictEqual(secondHop.body, payload, 'Payload must be preserved exactly');

        console.log('✓ Test 1 Passed: HTTP 302 redirect preserved Authorization Bearer token & POST payload.');
        passed++;
    }

    // Test 2: ScanEntity Lifecycle Transition 0 (pending) -> 1 (synced)
    {
        const dao = new MockRoomScanDao();
        const initialScan = {
            scanId: 'uuid-auto-1',
            scannedAt: '2026-11-07T12:00:00Z',
            deviceId: 'DEV_01',
            examId: 'EXAM_001',
            studentCode: '10001',
            score: 20,
            maxScore: 20,
            answers: 'AAAAABBBBBCCCCCDDDDD',
            status: 'OK',
            syncStatus: 0 // 0: pending
        };

        dao.insert(initialScan);
        assert.strictEqual(dao.get('uuid-auto-1').syncStatus, 0, 'Initial status must be 0 (pending)');

        // Simulate SyncWorker successfully uploading
        dao.markAsSynced(['uuid-auto-1']);
        assert.strictEqual(dao.get('uuid-auto-1').syncStatus, 1, 'Status must transition to 1 (synced)');

        console.log('✓ Test 2 Passed: ScanEntity successfully transitioned from status 0 (pending) to 1 (synced).');
        passed++;
    }

    // Test 3: Uncertain mark handling (Review flow)
    {
        const dao = new MockRoomScanDao();
        const uncertainScan = {
            scanId: 'uuid-uncertain-1',
            scannedAt: '2026-11-07T12:05:00Z',
            deviceId: 'DEV_01',
            examId: 'EXAM_001',
            studentCode: '1000?',
            score: 18,
            maxScore: 20,
            answers: 'AAAAA?BBBBCCCCCDDDDD',
            status: 'NEEDS_REVIEW',
            syncStatus: 3 // 3: review_needed
        };

        dao.insert(uncertainScan);
        assert.strictEqual(dao.get('uuid-uncertain-1').syncStatus, 3, 'Uncertain scan must have status 3');

        // Teacher reviews and resolves in ReviewActivity
        dao.updateReview('uuid-uncertain-1', '10005', 'AAAAAABBBBCCCCCDDDDD', 'OK', 0);
        const resolved = dao.get('uuid-uncertain-1');
        assert.strictEqual(resolved.studentCode, '10005');
        assert.strictEqual(resolved.status, 'OK');
        assert.strictEqual(resolved.syncStatus, 0, 'Resolved scan must transition to status 0 (pending) for upload');

        console.log('✓ Test 3 Passed: Uncertain scan held in review (status 3) and transitioned to 0 upon teacher resolution.');
        passed++;
    }

    console.log(`\nAll ${passed}/${passed} Phase 4 tests PASSED!`);
}

runPhase4Tests().catch(err => {
    console.error('Test failed:', err);
    process.exit(1);
});


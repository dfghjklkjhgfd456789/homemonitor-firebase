import test from 'node:test';
import assert from 'node:assert/strict';
import { InputBridge } from '../shared/input-bridge.js';

test('Input Bridge - Dispatches POST request to /api/input/gesture', async () => {
    const mockFetch = async (url, options) => {
        assert.equal(url, '/api/input/gesture');
        assert.equal(options.method, 'POST');
        assert.equal(options.headers['X-CSRF-Token'], 'test_csrf_123');

        return {
            ok: true,
            json: async () => ({ status: 'ok', requestId: 'req_123', strokeCount: 1 })
        };
    };

    const bridge = new InputBridge(mockFetch);
    bridge.setCsrfToken('test_csrf_123');

    const gesture = {
        schemaVersion: 1,
        requestId: 'req_123',
        strokes: [{ pointerId: 1, durationMs: 50, points: [{ u: 0.5, v: 0.5 }] }]
    };

    const res = await bridge.dispatchGesture(gesture);
    assert.equal(res.success, true);
    assert.equal(res.requestId, 'req_123');
});

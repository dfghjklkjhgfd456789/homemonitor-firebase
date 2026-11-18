import test from 'node:test';
import assert from 'node:assert/strict';
import { TelemetryCollector } from '../shared/telemetry-collector.js';

test('Telemetry Collector - Stage Timing and Completion Summary', () => {
    let mockTime = 1000;
    const mockPerf = { now: () => mockTime };

    const tc = new TelemetryCollector(mockPerf);
    tc.markStage('req_1', 'construct');

    mockTime = 1050;
    tc.markStage('req_1', 'send');

    mockTime = 1200;
    const rec = tc.completeRequest('req_1', 'COMPLETED');
    assert.equal(rec.totalDurationMs, 200);

    const summary = tc.getSummary();
    assert.equal(summary.totalRequests, 1);
    assert.equal(summary.completedRequests, 1);
    assert.equal(summary.completionRatePct, 100);
    assert.equal(summary.medianDurationMs, 200);
});

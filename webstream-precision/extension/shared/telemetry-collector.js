/**
 * WebStream Precision Control - Telemetry Collector (E6)
 * Measures browser performance stage timing, round-trip durations, and completion stats.
 */

export class TelemetryCollector {
    constructor(performanceRef = globalThis.performance) {
        this.perf = performanceRef || { now: () => Date.now() };
        this.records = new Map();
        this.history = [];
        this.maxHistory = 200;
    }

    markStage(requestId, stageName) {
        if (!requestId || !stageName) return;
        const now = this.perf.now();
        let rec = this.records.get(requestId);
        if (!rec) {
            rec = { requestId, startTime: now, stages: {} };
            this.records.set(requestId, rec);
        }
        rec.stages[stageName] = now;
    }

    completeRequest(requestId, status = 'COMPLETED') {
        const rec = this.records.get(requestId);
        if (!rec) return null;

        const endTime = this.perf.now();
        rec.endTime = endTime;
        rec.status = status;
        rec.totalDurationMs = Math.max(0, endTime - rec.startTime);

        this.records.delete(requestId);
        this.history.push(rec);

        if (this.history.length > this.maxHistory) {
            this.history.shift();
        }

        return rec;
    }

    getSummary() {
        const total = this.history.length;
        if (total === 0) {
            return {
                totalRequests: 0,
                completedRequests: 0,
                cancelledRequests: 0,
                medianDurationMs: 0,
                p95DurationMs: 0,
                completionRatePct: 100
            };
        }

        const completed = this.history.filter(h => h.status === 'COMPLETED').length;
        const cancelled = this.history.filter(h => h.status === 'CANCELLED').length;
        const durations = this.history.map(h => h.totalDurationMs).sort((a, b) => a - b);

        const medianDurationMs = durations[Math.floor(durations.length / 2)];
        const p95Idx = Math.min(Math.floor(durations.length * 0.95), durations.length - 1);
        const p95DurationMs = durations[p95Idx];

        return {
            totalRequests: total,
            completedRequests: completed,
            cancelledRequests: cancelled,
            medianDurationMs,
            p95DurationMs,
            completionRatePct: (completed / total) * 100
        };
    }

    reset() {
        this.records.clear();
        this.history = [];
    }
}

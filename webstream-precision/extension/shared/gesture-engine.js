/**
 * WebStream Precision Control - Single-Pointer Gesture Engine (E5)
 * Converts single pointer events into structured gesture requests.
 */

export const DEFAULT_THRESHOLDS = {
    tapMaxDurationMs: 300,
    tapMovementThresholdCssPx: 3,
    swipeMovementThresholdCssPx: 5,
    longPressDurationMs: 500,
    pathSampleIntervalMs: 16,
    maximumPathPoints: 64
};

let requestCounter = 1;

export function generateRequestId() {
    return `req_${Date.now()}_${requestCounter++}`;
}

export function isTap(downEvent, upEvent, thresholds = DEFAULT_THRESHOLDS) {
    if (!downEvent || !upEvent) return false;
    const duration = upEvent.time - downEvent.time;
    if (duration > thresholds.tapMaxDurationMs) return false;

    const dx = Math.abs(upEvent.clientX - downEvent.clientX);
    const dy = Math.abs(upEvent.clientY - downEvent.clientY);
    return dx <= thresholds.tapMovementThresholdCssPx && dy <= thresholds.tapMovementThresholdCssPx;
}

export function buildTapGesture(u, v, geometryVersion = 1) {
    return {
        schemaVersion: 1,
        requestId: generateRequestId(),
        geometryVersion,
        strokes: [
            {
                pointerId: 1,
                startOffsetMs: 0,
                durationMs: 50,
                points: [{ u, v }]
            }
        ]
    };
}

export function buildSwipeGesture(startU, startV, endU, endV, durationMs = 300, geometryVersion = 1) {
    const clampedDuration = Math.min(Math.max(durationMs, 50), 3000);
    return {
        schemaVersion: 1,
        requestId: generateRequestId(),
        geometryVersion,
        strokes: [
            {
                pointerId: 1,
                startOffsetMs: 0,
                durationMs: clampedDuration,
                points: [
                    { u: startU, v: startV },
                    { u: endU, v: endV }
                ]
            }
        ]
    };
}

export function buildLongPressGesture(u, v, durationMs = 500, geometryVersion = 1) {
    const clampedDuration = Math.min(Math.max(durationMs, 500), 3000);
    return {
        schemaVersion: 1,
        requestId: generateRequestId(),
        geometryVersion,
        strokes: [
            {
                pointerId: 1,
                startOffsetMs: 0,
                durationMs: clampedDuration,
                points: [{ u, v }]
            }
        ]
    };
}

export function buildDragPathGesture(normalizedPoints, durationMs = 500, geometryVersion = 1) {
    if (!Array.isArray(normalizedPoints) || normalizedPoints.length === 0) return null;
    const points = normalizedPoints.slice(0, DEFAULT_THRESHOLDS.maximumPathPoints);
    const clampedDuration = Math.min(Math.max(durationMs, 100), 5000);

    return {
        schemaVersion: 1,
        requestId: generateRequestId(),
        geometryVersion,
        strokes: [
            {
                pointerId: 1,
                startOffsetMs: 0,
                durationMs: clampedDuration,
                points
            }
        ]
    };
}

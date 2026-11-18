/**
 * WebStream Precision Control - Multi-Touch Pinch Engine (E5)
 * Calculates two-pointer pinch/zoom scale factors and builds parallel stroke payloads for Android.
 */

import { generateRequestId } from './gesture-engine.js';

export function distance(p1, p2) {
    const dx = p1.clientX - p2.clientX;
    const dy = p1.clientY - p2.clientY;
    return Math.sqrt(dx * dx + dy * dy);
}

export function calculateMidpoint(p1, p2) {
    return {
        u: (p1.u + p2.u) / 2,
        v: (p1.v + p2.v) / 2
    };
}

export function calculatePinch(p1Start, p2Start, p1Current, p2Current) {
    if (!p1Start || !p2Start || !p1Current || !p2Current) return null;

    const initialDist = distance(p1Start, p2Start);
    const currentDist = distance(p1Current, p2Current);

    if (initialDist <= 0.001) return null;

    const scaleFactor = currentDist / initialDist;
    const midpoint = calculateMidpoint(p1Current, p2Current);

    return {
        initialDist,
        currentDist,
        scaleFactor,
        midpoint,
        direction: scaleFactor >= 1.0 ? 'zoom_in' : 'zoom_out'
    };
}

export function buildPinchGesture(p1StartNorm, p1EndNorm, p2StartNorm, p2EndNorm, durationMs = 500, geometryVersion = 1) {
    const clampedDuration = Math.min(Math.max(durationMs, 100), 3000);

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
                    { u: p1StartNorm.u, v: p1StartNorm.v },
                    { u: p1EndNorm.u, v: p1EndNorm.v }
                ]
            },
            {
                pointerId: 2,
                startOffsetMs: 0,
                durationMs: clampedDuration,
                points: [
                    { u: p2StartNorm.u, v: p2StartNorm.v },
                    { u: p2EndNorm.u, v: p2EndNorm.v }
                ]
            }
        ]
    };
}

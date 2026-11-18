import test from 'node:test';
import assert from 'node:assert/strict';
import { distance, calculateMidpoint, calculatePinch, buildPinchGesture } from '../shared/multitouch-engine.js';

test('Multi-Touch Engine - Distance & Midpoint Calculation', () => {
    const p1 = { clientX: 0, clientY: 0, u: 0.0, v: 0.0 };
    const p2 = { clientX: 3, clientY: 4, u: 1.0, v: 1.0 };

    assert.equal(distance(p1, p2), 5);
    const mid = calculateMidpoint(p1, p2);
    assert.equal(mid.u, 0.5);
    assert.equal(mid.v, 0.5);
});

test('Multi-Touch Engine - Pinch Calculation (Zoom In / Zoom Out)', () => {
    const p1Start = { clientX: 100, clientY: 100, u: 0.4, v: 0.5 };
    const p2Start = { clientX: 200, clientY: 100, u: 0.6, v: 0.5 };

    const p1In = { clientX: 50, clientY: 100, u: 0.2, v: 0.5 };
    const p2In = { clientX: 250, clientY: 100, u: 0.8, v: 0.5 };

    const resIn = calculatePinch(p1Start, p2Start, p1In, p2In);
    assert.equal(resIn.direction, 'zoom_in');
    assert.equal(resIn.scaleFactor, 2.0);
});

test('Multi-Touch Engine - Build Pinch Payload', () => {
    const p1S = { u: 0.4, v: 0.5 };
    const p1E = { u: 0.2, v: 0.5 };
    const p2S = { u: 0.6, v: 0.5 };
    const p2E = { u: 0.8, v: 0.5 };

    const payload = buildPinchGesture(p1S, p1E, p2S, p2E, 300, 5);
    assert.equal(payload.strokes.length, 2);
    assert.equal(payload.strokes[0].pointerId, 1);
    assert.equal(payload.strokes[1].pointerId, 2);
});

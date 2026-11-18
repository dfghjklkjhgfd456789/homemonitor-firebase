import test from 'node:test';
import assert from 'node:assert/strict';
import {
    isTap,
    buildTapGesture,
    buildSwipeGesture,
    buildLongPressGesture,
    buildDragPathGesture
} from '../shared/gesture-engine.js';

test('Gesture Engine - Tap Recognition', () => {
    const downEvt = { clientX: 100, clientY: 100, time: 1000 };
    const upEvtTap = { clientX: 101, clientY: 101, time: 1150 };
    const upEvtDrag = { clientX: 120, clientY: 100, time: 1150 };
    const upEvtSlow = { clientX: 100, clientY: 100, time: 1500 };

    assert.equal(isTap(downEvt, upEvtTap), true);
    assert.equal(isTap(downEvt, upEvtDrag), false);
    assert.equal(isTap(downEvt, upEvtSlow), false);
});

test('Gesture Engine - Build Tap Payload', () => {
    const payload = buildTapGesture(0.5, 0.5, 3);
    assert.equal(payload.schemaVersion, 1);
    assert.equal(payload.geometryVersion, 3);
    assert.equal(payload.strokes.length, 1);
    assert.equal(payload.strokes[0].points[0].u, 0.5);
});

test('Gesture Engine - Build Long Press Payload', () => {
    const payload = buildLongPressGesture(0.5, 0.5, 750, 2);
    assert.equal(payload.strokes[0].durationMs, 750);
});

test('Gesture Engine - Build Drag Path Payload', () => {
    const points = [{ u: 0.1, v: 0.1 }, { u: 0.2, v: 0.2 }, { u: 0.3, v: 0.3 }];
    const payload = buildDragPathGesture(points, 400, 1);
    assert.equal(payload.strokes[0].points.length, 3);
    assert.equal(payload.strokes[0].durationMs, 400);
});

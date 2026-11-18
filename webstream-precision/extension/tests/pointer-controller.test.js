import test from 'node:test';
import assert from 'node:assert/strict';
import { PointerController } from '../shared/pointer-controller.js';

test('Pointer Controller - Single Pointer Tap Flow', () => {
    const mockMap = (x, y) => ({ valid: true, u: x / 1000, v: y / 1000 });
    const ctrl = new PointerController(mockMap);

    ctrl.handlePointerDown({ pointerId: 1, clientX: 500, clientY: 500 });
    const gesture = ctrl.handlePointerUp({ pointerId: 1, clientX: 500, clientY: 500 }, 2);

    assert.ok(gesture !== null);
    assert.equal(gesture.strokes[0].points[0].u, 0.5);
    assert.equal(gesture.geometryVersion, 2);
});

test('Pointer Controller - Mode Switching', () => {
    const ctrl = new PointerController(() => ({ valid: true, u: 0.5, v: 0.5 }));
    assert.equal(ctrl.mode, 'LEGACY');
    ctrl.setMode('PRECISION');
    assert.equal(ctrl.mode, 'PRECISION');
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { getNinePointGrid, computeErrorVectors } from '../shared/calibration-engine.js';

test('Calibration Engine - Nine-Point Grid Generator', () => {
    const grid = getNinePointGrid();
    assert.equal(grid.length, 9);
    assert.equal(grid[0].u, 0.10);
    assert.equal(grid[0].v, 0.10);
    assert.equal(grid[4].u, 0.50); // Center
    assert.equal(grid[4].v, 0.50);
});

test('Calibration Engine - Compute Error Vectors', () => {
    const targets = getNinePointGrid();
    const observed = targets.map(t => ({ x: t.u * 1080, y: t.v * 2400 }));

    const res = computeErrorVectors(targets, observed, 1080, 2400);
    assert.equal(res.valid, true);
    assert.equal(Math.round(res.meanErrorPx), 0);
    assert.equal(Math.round(res.p95ErrorPx), 0);
    assert.equal(res.normalizedErrorPct, 0);
});

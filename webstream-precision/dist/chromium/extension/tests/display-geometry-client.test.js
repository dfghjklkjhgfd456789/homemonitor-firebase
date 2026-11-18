import test from 'node:test';
import assert from 'node:assert/strict';
import {
    validateGeometrySchema,
    compareGeometryWithE3Intrinsic,
    fetchDisplayGeometry
} from '../shared/display-geometry-client.js';

test('Display Geometry Client - Schema Validation', () => {
    const validData = {
        schemaVersion: 1,
        geometryVersion: 1,
        screenshotWidth: 1080,
        screenshotHeight: 2400,
        inputWidth: 1080,
        inputHeight: 2400,
        rotation: 0,
        isValid: true
    };
    assert.equal(validateGeometrySchema(validData).valid, true);

    const invalidData = { screenshotWidth: 0, screenshotHeight: 2400, isValid: true };
    assert.equal(validateGeometrySchema(invalidData).valid, false);

    const staleData = { screenshotWidth: 1080, screenshotHeight: 2400, isValid: false };
    assert.equal(validateGeometrySchema(staleData).valid, false);
});

test('Display Geometry Client - E3 Intrinsic Comparison', () => {
    const androidGeo = {
        screenshotWidth: 1080,
        screenshotHeight: 2400,
        inputWidth: 1080,
        inputHeight: 2400,
        geometryVersion: 1,
        isValid: true
    };

    // Exact match
    assert.equal(compareGeometryWithE3Intrinsic(androidGeo, 1080, 2400).matched, true);

    // Mismatch
    const mismatch = compareGeometryWithE3Intrinsic(androidGeo, 720, 1600);
    assert.equal(mismatch.matched, false);
    assert.ok(mismatch.reason.includes('Dimension mismatch'));
});

test('Display Geometry Client - Fetching Mocked API', async () => {
    const mockFetch = async (url) => {
        if (url === '/api/metadata') {
            return {
                ok: true,
                json: async () => ({
                    schemaVersion: 1,
                    geometryVersion: 1,
                    screenshotWidth: 1080,
                    screenshotHeight: 2400,
                    inputWidth: 1080,
                    inputHeight: 2400,
                    rotation: 0,
                    isValid: true
                })
            };
        }
        return { ok: false, status: 404 };
    };

    const res = await fetchDisplayGeometry(mockFetch);
    assert.equal(res.success, true);
    assert.equal(res.geometry.screenshotWidth, 1080);
});

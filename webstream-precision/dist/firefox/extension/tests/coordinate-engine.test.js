import test from 'node:test';
import assert from 'node:assert/strict';
import {
    getImageGeometry,
    mapClientPointToNormalized,
    validateNormalizedPoint,
    parseObjectPosition
} from '../shared/coordinate-engine.js';

test('Coordinate Engine - Basic Contain Layout (1080x2400 in 400x800 container)', () => {
    const mockLayout = {
        naturalWidth: 1080,
        naturalHeight: 2400,
        borderBox: { left: 0, top: 0, width: 400, height: 800 },
        objectFit: 'contain',
        objectPosition: '50% 50%'
    };

    const geo = getImageGeometry(null, mockLayout);
    assert.equal(geo.status, 'valid');

    // Scale = min(400/1080 = 0.37037, 800/2400 = 0.33333) = 0.33333
    // Rendered width = 1080 * (1/3) = 360, height = 2400 * (1/3) = 800
    assert.equal(Math.round(geo.renderedImage.width), 360);
    assert.equal(Math.round(geo.renderedImage.height), 800);

    // Pillarbox offset X = (400 - 360) / 2 = 20
    assert.equal(Math.round(geo.renderedImage.left), 20);

    // Center tap
    const centerRes = mapClientPointToNormalized(200, 400, geo);
    assert.equal(centerRes.valid, true);
    assert.equal(centerRes.u, 0.5);
    assert.equal(centerRes.v, 0.5);
    assert.equal(centerRes.sourceX, 540);
    assert.equal(centerRes.sourceY, 1200);
});

test('Coordinate Engine - Top-Left and Bottom-Right Corners (Contain)', () => {
    const mockLayout = {
        naturalWidth: 1080,
        naturalHeight: 2400,
        borderBox: { left: 0, top: 0, width: 400, height: 800 },
        objectFit: 'contain'
    };

    const geo = getImageGeometry(null, mockLayout);

    // Top-left visible image is at clientX=20, clientY=0
    const topLeftRes = mapClientPointToNormalized(20, 0, geo);
    assert.equal(topLeftRes.valid, true);
    assert.equal(topLeftRes.u, 0);
    assert.equal(topLeftRes.v, 0);

    // Bottom-right visible image is at clientX=380, clientY=800
    const bottomRightRes = mapClientPointToNormalized(380, 800, geo);
    assert.equal(bottomRightRes.valid, true);
    assert.equal(bottomRightRes.u, 1);
    assert.equal(bottomRightRes.v, 1);
});

test('Coordinate Engine - Letterbox Rejection', () => {
    const mockLayout = {
        naturalWidth: 1080,
        naturalHeight: 2400,
        borderBox: { left: 0, top: 0, width: 400, height: 800 },
        objectFit: 'contain'
    };

    const geo = getImageGeometry(null, mockLayout);

    // Click at clientX=10 (in left pillarbox margin)
    const letterboxRes = mapClientPointToNormalized(10, 400, geo);
    assert.equal(letterboxRes.valid, false);
    assert.ok(letterboxRes.reason.includes('letterbox'));
});

test('Coordinate Engine - Object-Fit Cover Layout', () => {
    const mockLayout = {
        naturalWidth: 1080,
        naturalHeight: 2400,
        borderBox: { left: 0, top: 0, width: 400, height: 800 },
        objectFit: 'cover'
    };

    const geo = getImageGeometry(null, mockLayout);
    // Scale = max(400/1080 = 0.37037, 800/2400 = 0.33333) = 0.37037
    // Rendered width = 400, height = 2400 * (400/1080) = 888.88
    assert.equal(Math.round(geo.renderedImage.width), 400);

    // Center click at (200, 400)
    const centerRes = mapClientPointToNormalized(200, 400, geo);
    assert.equal(centerRes.valid, true);
    assert.equal(centerRes.u, 0.5);
    assert.equal(centerRes.v, 0.5);
});

test('Coordinate Engine - Object-Fit Fill Layout', () => {
    const mockLayout = {
        naturalWidth: 1080,
        naturalHeight: 2400,
        borderBox: { left: 0, top: 0, width: 400, height: 800 },
        objectFit: 'fill'
    };

    const geo = getImageGeometry(null, mockLayout);
    assert.equal(geo.renderedImage.width, 400);
    assert.equal(geo.renderedImage.height, 800);

    const cornerRes = mapClientPointToNormalized(400, 800, geo);
    assert.equal(cornerRes.valid, true);
    assert.equal(cornerRes.u, 1.0);
    assert.equal(cornerRes.v, 1.0);
});

test('Coordinate Engine - CSS Box Model (Borders and Padding)', () => {
    const mockLayout = {
        naturalWidth: 1000,
        naturalHeight: 1000,
        borderBox: { left: 10, top: 10, width: 220, height: 220 },
        borderLeft: 10, borderRight: 10, borderTop: 10, borderBottom: 10,
        paddingLeft: 0, paddingRight: 0, paddingTop: 0, paddingBottom: 0,
        objectFit: 'fill'
    };

    const geo = getImageGeometry(null, mockLayout);
    // Content box: left=20, top=20, width=200, height=200
    assert.equal(geo.contentBox.left, 20);
    assert.equal(geo.contentBox.top, 20);
    assert.equal(geo.contentBox.width, 200);

    // Click at border (clientX=15) -> outside content box
    const borderClick = mapClientPointToNormalized(15, 50, geo);
    assert.equal(borderClick.valid, false);

    // Click inside content box (clientX=120, clientY=120) -> center
    const insideClick = mapClientPointToNormalized(120, 120, geo);
    assert.equal(insideClick.valid, true);
    assert.equal(insideClick.u, 0.5);
    assert.equal(insideClick.v, 0.5);
});

test('Coordinate Engine - Object-Position Top Left', () => {
    const mockLayout = {
        naturalWidth: 1080,
        naturalHeight: 2400,
        borderBox: { left: 0, top: 0, width: 400, height: 800 },
        objectFit: 'contain',
        objectPosition: 'top left'
    };

    const geo = getImageGeometry(null, mockLayout);
    assert.equal(geo.renderedImage.left, 0);

    const res = mapClientPointToNormalized(0, 0, geo);
    assert.equal(res.valid, true);
    assert.equal(res.u, 0);
    assert.equal(res.v, 0);
});

test('Coordinate Engine - Object-Position Pixel Offsets (10px 20px)', () => {
    const mockLayout = {
        naturalWidth: 100,
        naturalHeight: 100,
        borderBox: { left: 0, top: 0, width: 200, height: 200 },
        objectFit: 'none',
        objectPosition: '10px 20px'
    };

    const geo = getImageGeometry(null, mockLayout);
    assert.equal(geo.renderedImage.left, 10);
    assert.equal(geo.renderedImage.top, 20);

    const res = mapClientPointToNormalized(10, 20, geo);
    assert.equal(res.valid, true);
    assert.equal(res.u, 0);
    assert.equal(res.v, 0);
});

test('Coordinate Engine - Rejects Unsupported Rotation/Skew Transform Matrix', () => {
    const mockLayout = {
        naturalWidth: 1080,
        naturalHeight: 2400,
        borderBox: { left: 0, top: 0, width: 400, height: 800 },
        transform: 'matrix(0, 1, -1, 0, 0, 0)' // 90 deg rotation
    };

    const geo = getImageGeometry(null, mockLayout);
    assert.equal(geo.supportedTransform, false);

    const res = mapClientPointToNormalized(200, 400, geo);
    assert.equal(res.valid, false);
    assert.ok(res.reason.includes('Rotation'));
});

test('Coordinate Engine - Normalization Validation Helper', () => {
    assert.equal(validateNormalizedPoint(0.5, 0.5), true);
    assert.equal(validateNormalizedPoint(0.0, 1.0), true);
    assert.equal(validateNormalizedPoint(-0.1, 0.5), false);
    assert.equal(validateNormalizedPoint(1.1, 0.5), false);
    assert.equal(validateNormalizedPoint(NaN, 0.5), false);
    assert.equal(validateNormalizedPoint(Infinity, 0.5), false);
});

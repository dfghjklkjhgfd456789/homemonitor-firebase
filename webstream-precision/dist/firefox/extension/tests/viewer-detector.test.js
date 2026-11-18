import test from 'node:test';
import assert from 'node:assert/strict';
import { detectViewer } from '../shared/viewer-detector.js';

test('Viewer Detector - Detects valid img#stream element', () => {
    const mockImg = {
        tagName: 'IMG',
        getAttribute: (attr) => (attr === 'src' ? '/stream' : null),
        isConnected: true,
        naturalWidth: 1080,
        naturalHeight: 2400,
        getBoundingClientRect: () => ({ left: 0, top: 0, width: 360, height: 800 })
    };

    const mockDoc = {
        querySelector: (sel) => (sel === 'img#stream' ? mockImg : null)
    };

    const result = detectViewer(mockDoc);
    assert.equal(result.detected, true);
    assert.equal(result.naturalWidth, 1080);
    assert.equal(result.naturalHeight, 2400);
    assert.equal(result.renderedWidth, 360);
    assert.equal(result.renderedHeight, 800);
});

test('Viewer Detector - Fails when element is missing', () => {
    const mockDoc = { querySelector: () => null };
    const result = detectViewer(mockDoc);
    assert.equal(result.detected, false);
});

test('Viewer Detector - Fails when image has zero intrinsic dimensions', () => {
    const mockImg = {
        tagName: 'IMG',
        getAttribute: () => '/stream',
        isConnected: true,
        naturalWidth: 0,
        naturalHeight: 0,
        getBoundingClientRect: () => ({ left: 0, top: 0, width: 0, height: 0 })
    };
    const mockDoc = { querySelector: () => mockImg };
    const result = detectViewer(mockDoc);
    assert.equal(result.detected, false);
});

test('Viewer Detector - Fails when src does not match stream path', () => {
    const mockImg = {
        tagName: 'IMG',
        getAttribute: () => '/static/logo.png',
        isConnected: true,
        naturalWidth: 100,
        naturalHeight: 100,
        getBoundingClientRect: () => ({ left: 0, top: 0, width: 100, height: 100 })
    };
    const mockDoc = { querySelector: () => mockImg };
    const result = detectViewer(mockDoc);
    assert.equal(result.detected, false);
});

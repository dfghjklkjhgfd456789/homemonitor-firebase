/**
 * WebStream Precision Control - Viewer Detector
 * Identifies and validates active WebStream stream image elements on the page.
 */

import { VIEWER_SELECTOR, STREAM_PATH_KEYWORD } from './constants.js';

export function detectViewer(documentRef = globalThis.document) {
    if (!documentRef) {
        return {
            detected: false,
            reason: 'Document reference unavailable'
        };
    }

    const imgElement = documentRef.querySelector(VIEWER_SELECTOR);

    if (!imgElement) {
        return {
            detected: false,
            reason: `No element matching selector '${VIEWER_SELECTOR}' found`
        };
    }

    if (imgElement.tagName.toLowerCase() !== 'img') {
        return {
            detected: false,
            reason: `Matched element is not an <img> tag`
        };
    }

    const src = imgElement.getAttribute('src') || '';
    if (!src.includes(STREAM_PATH_KEYWORD) && !src.includes('stream')) {
        return {
            detected: false,
            reason: `Image src '${src}' does not match WebStream stream path`
        };
    }

    const isConnected = imgElement.isConnected;
    const naturalWidth = imgElement.naturalWidth || 0;
    const naturalHeight = imgElement.naturalHeight || 0;

    const rect = imgElement.getBoundingClientRect ? imgElement.getBoundingClientRect() : { left: 0, top: 0, width: 0, height: 0 };
    const renderedWidth = rect.width;
    const renderedHeight = rect.height;

    const isVisible = renderedWidth > 0 && renderedHeight > 0 &&
                      globalThis.getComputedStyle ? globalThis.getComputedStyle(imgElement).display !== 'none' : true;

    return {
        detected: isConnected && isVisible && naturalWidth > 0 && naturalHeight > 0,
        selector: VIEWER_SELECTOR,
        element: imgElement,
        naturalWidth,
        naturalHeight,
        renderedWidth,
        renderedHeight,
        connected: isConnected,
        visible: isVisible,
        boundingClientRect: {
            left: rect.left,
            top: rect.top,
            width: rect.width,
            height: rect.height
        }
    };
}

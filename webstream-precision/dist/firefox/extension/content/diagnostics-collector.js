/**
 * WebStream Precision Control - Diagnostics Collector
 */

import { detectViewer } from '../shared/viewer-detector.js';
import { getDisplayDiagnostics } from '../shared/display-diagnostics.js';

export function collectFullDiagnostics(windowRef = globalThis.window, documentRef = globalThis.document) {
    const viewerData = detectViewer(documentRef);
    const displayData = getDisplayDiagnostics(windowRef);

    return {
        timestamp: Date.now(),
        display: displayData,
        viewer: {
            detected: viewerData.detected,
            selector: viewerData.selector,
            naturalWidth: viewerData.naturalWidth,
            naturalHeight: viewerData.naturalHeight,
            renderedWidth: viewerData.renderedWidth,
            renderedHeight: viewerData.renderedHeight,
            visible: viewerData.visible,
            connected: viewerData.connected,
            boundingClientRect: viewerData.boundingClientRect
        }
    };
}

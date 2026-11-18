/**
 * WebStream Precision Control - Viewer Observer
 * Monitors the active stream image element for dimension, DOM, and resize changes.
 */

import { detectViewer } from '../shared/viewer-detector.js';

export class ViewerObserver {
    constructor(onViewerChangeCallback, windowRef = globalThis.window, documentRef = globalThis.document) {
        this.onViewerChange = onViewerChangeCallback;
        this.window = windowRef;
        this.document = documentRef;
        this.resizeObserver = null;
        this.mutationObserver = null;
        this.activeViewerData = null;
    }

    start() {
        this.evaluateViewer();

        if (this.window.ResizeObserver) {
            this.resizeObserver = new this.window.ResizeObserver(() => {
                this.evaluateViewer();
            });
            this.resizeObserver.observe(this.document.body);
        }

        if (this.window.MutationObserver) {
            this.mutationObserver = new this.window.MutationObserver(() => {
                this.evaluateViewer();
            });
            this.mutationObserver.observe(this.document.body, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['src', 'style', 'class']
            });
        }

        this.handleResize = () => this.evaluateViewer();
        this.window.addEventListener('resize', this.handleResize);
        this.window.addEventListener('scroll', this.handleResize);
    }

    evaluateViewer() {
        const data = detectViewer(this.document);
        this.activeViewerData = data;
        if (typeof this.onViewerChange === 'function') {
            this.onViewerChange(data);
        }
    }

    stop() {
        if (this.resizeObserver) {
            this.resizeObserver.disconnect();
            this.resizeObserver = null;
        }
        if (this.mutationObserver) {
            this.mutationObserver.disconnect();
            this.mutationObserver = null;
        }
        if (this.handleResize) {
            this.window.removeEventListener('resize', this.handleResize);
            this.window.removeEventListener('scroll', this.handleResize);
        }
    }
}

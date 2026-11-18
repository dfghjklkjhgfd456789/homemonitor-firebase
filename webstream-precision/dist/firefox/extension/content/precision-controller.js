/**
 * WebStream Precision Control - Precision Controller (E3 Engine Update)
 */

import { OverlayManager } from './overlay-manager.js';
import { ViewerObserver } from './viewer-observer.js';
import { collectFullDiagnostics } from './diagnostics-collector.js';
import { getImageGeometry, mapClientPointToNormalized } from '../shared/coordinate-engine.js';
import { MESSAGE_TYPES } from '../shared/message-types.js';

export class PrecisionController {
    constructor(windowRef = globalThis.window, documentRef = globalThis.document) {
        this.window = windowRef;
        this.document = documentRef;
        this.overlayManager = new OverlayManager(documentRef, windowRef);
        this.viewerObserver = null;
        this.enabled = true;
        this.currentGeometry = null;
    }

    init() {
        this.overlayManager.enable();
        this.viewerObserver = new ViewerObserver((viewerData) => {
            if (viewerData && viewerData.element) {
                this.currentGeometry = getImageGeometry(viewerData.element);
                this.overlayManager.updatePosition(viewerData, this.currentGeometry);
            } else {
                this.overlayManager.updatePosition(viewerData, null);
            }
        }, this.window, this.document);
        this.viewerObserver.start();

        // Read-only passive pointer observation for non-interactive diagnostics
        this.handlePointerMove = (e) => {
            if (this.enabled && this.currentGeometry) {
                this.overlayManager.updatePointerReadout(e.clientX, e.clientY);
            }
        };

        this.window.addEventListener('pointermove', this.handlePointerMove, { passive: true });
    }

    toggleOverlay(state) {
        if (typeof state === 'boolean') {
            this.enabled = state;
        } else {
            this.enabled = !this.enabled;
        }

        if (this.enabled) {
            this.overlayManager.enable();
            if (this.viewerObserver && this.viewerObserver.activeViewerData) {
                const viewerData = this.viewerObserver.activeViewerData;
                if (viewerData.element) {
                    this.currentGeometry = getImageGeometry(viewerData.element);
                }
                this.overlayManager.updatePosition(viewerData, this.currentGeometry);
            }
        } else {
            this.overlayManager.disable();
        }

        return this.enabled;
    }

    getDiagnostics() {
        const diag = collectFullDiagnostics(this.window, this.document);
        if (this.viewerObserver && this.viewerObserver.activeViewerData && this.viewerObserver.activeViewerData.element) {
            diag.geometry = getImageGeometry(this.viewerObserver.activeViewerData.element);
        }
        return diag;
    }

    handleMessage(message, sendResponse) {
        if (!message || !message.type) return;

        switch (message.type) {
            case MESSAGE_TYPES.PING:
                sendResponse({ status: 'pong', active: true });
                break;
            case MESSAGE_TYPES.GET_DIAGNOSTICS:
                sendResponse(this.getDiagnostics());
                break;
            case MESSAGE_TYPES.TOGGLE_OVERLAY:
                const newState = this.toggleOverlay(message.enabled);
                sendResponse({ enabled: newState });
                break;
        }
    }

    destroy() {
        if (this.handlePointerMove) {
            this.window.removeEventListener('pointermove', this.handlePointerMove);
        }
        if (this.viewerObserver) {
            this.viewerObserver.stop();
            this.viewerObserver = null;
        }
        this.overlayManager.destroy();
    }
}

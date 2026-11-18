/**
 * WebStream Precision Control - Overlay Manager (E3 Diagnostics Update)
 * Renders a non-interactive visual bounding frame around the active WebStream screen.
 * Displays normalized (u, v) sub-pixel coordinate readouts during non-interactive pointer observation.
 * CRITICAL: Completely non-interactive (pointer-events: none). Does NOT intercept inputs.
 */

import { mapClientPointToNormalized } from '../shared/coordinate-engine.js';

export class OverlayManager {
    constructor(documentRef = globalThis.document, windowRef = globalThis.window) {
        this.document = documentRef;
        this.window = windowRef;
        this.overlayElement = null;
        this.badgeElement = null;
        this.coordBadgeElement = null;
        this.enabled = false;
        this.latestGeometry = null;
    }

    createOverlayElement() {
        if (this.overlayElement) return;

        const overlay = this.document.createElement('div');
        overlay.id = 'webstream-precision-overlay';
        overlay.style.cssText = `
            pointer-events: none !important;
            position: absolute !important;
            z-index: 999990 !important;
            border: 2px dashed #38bdf8 !important;
            border-radius: 8px !important;
            box-shadow: 0 0 14px rgba(56, 189, 248, 0.45) !important;
            box-sizing: border-box !important;
            display: none;
            transition: all 0.08s ease-out !important;
        `;

        const badge = this.document.createElement('div');
        badge.id = 'webstream-precision-badge';
        badge.style.cssText = `
            pointer-events: none !important;
            position: absolute !important;
            top: -24px !important;
            right: 0 !important;
            background: #0f172a !important;
            color: #38bdf8 !important;
            font-family: system-ui, -apple-system, sans-serif !important;
            font-size: 10px !important;
            font-weight: 700 !important;
            padding: 2px 6px !important;
            border-radius: 4px !important;
            border: 1px solid #334155 !important;
            box-shadow: 0 4px 6px rgba(0,0,0,0.3) !important;
            letter-spacing: 0.03em !important;
            white-space: nowrap !important;
        `;

        const coordBadge = this.document.createElement('div');
        coordBadge.id = 'webstream-precision-coord-badge';
        coordBadge.style.cssText = `
            pointer-events: none !important;
            position: absolute !important;
            bottom: -22px !important;
            left: 0 !important;
            background: #1e1b4b !important;
            color: #c084fc !important;
            font-family: ui-monospace, monospace !important;
            font-size: 10px !important;
            font-weight: 600 !important;
            padding: 2px 6px !important;
            border-radius: 4px !important;
            border: 1px solid #4338ca !important;
            box-shadow: 0 4px 6px rgba(0,0,0,0.3) !important;
            white-space: nowrap !important;
        `;

        overlay.appendChild(badge);
        overlay.appendChild(coordBadge);
        this.document.body.appendChild(overlay);
        this.overlayElement = overlay;
        this.badgeElement = badge;
        this.coordBadgeElement = coordBadge;
    }

    updatePosition(viewerData, geometry = null) {
        if (!this.enabled || !viewerData || !viewerData.detected) {
            this.hide();
            return;
        }

        if (geometry) {
            this.latestGeometry = geometry;
        }

        if (!this.overlayElement) {
            this.createOverlayElement();
        }

        const rect = viewerData.boundingClientRect;
        const scrollX = this.window.scrollX || 0;
        const scrollY = this.window.scrollY || 0;

        const top = rect.top + scrollY;
        const left = rect.left + scrollX;
        const width = rect.width;
        const height = rect.height;

        this.overlayElement.style.top = `${top}px`;
        this.overlayElement.style.left = `${left}px`;
        this.overlayElement.style.width = `${width}px`;
        this.overlayElement.style.height = `${height}px`;
        this.overlayElement.style.display = 'block';

        const dpr = this.window.devicePixelRatio || 1;
        const fitMode = this.latestGeometry ? this.latestGeometry.objectFit : 'contain';
        this.badgeElement.textContent = `🎯 WebStream E3 Engine | ${viewerData.naturalWidth}x${viewerData.naturalHeight} | ${Math.round(width)}x${Math.round(height)} | fit: ${fitMode} (DPR ${dpr})`;

        if (!this.coordBadgeElement.textContent) {
            this.coordBadgeElement.textContent = `u: 0.500000 | v: 0.500000 (Center Baseline)`;
        }
    }

    updatePointerReadout(clientX, clientY) {
        if (!this.enabled || !this.coordBadgeElement || !this.latestGeometry) return;
        const mapping = mapClientPointToNormalized(clientX, clientY, this.latestGeometry);

        if (mapping.valid) {
            this.coordBadgeElement.textContent = `u: ${mapping.u.toFixed(6)} | v: ${mapping.v.toFixed(6)} | source: (${Math.round(mapping.sourceX)}, ${Math.round(mapping.sourceY)})`;
            this.coordBadgeElement.style.borderColor = '#4338ca';
            this.coordBadgeElement.style.color = '#c084fc';
        } else {
            this.coordBadgeElement.textContent = `[Out of Bounds / ${mapping.reason || 'Letterbox'}]`;
            this.coordBadgeElement.style.borderColor = '#991b1b';
            this.coordBadgeElement.style.color = '#fca5a5';
        }
    }

    enable() {
        this.enabled = true;
        if (this.overlayElement) {
            this.overlayElement.style.display = 'block';
        }
    }

    disable() {
        this.enabled = false;
        this.hide();
    }

    hide() {
        if (this.overlayElement) {
            this.overlayElement.style.display = 'none';
        }
    }

    destroy() {
        if (this.overlayElement && this.overlayElement.parentNode) {
            this.overlayElement.parentNode.removeChild(this.overlayElement);
        }
        this.overlayElement = null;
        this.badgeElement = null;
        this.coordBadgeElement = null;
        this.enabled = false;
        this.latestGeometry = null;
    }
}

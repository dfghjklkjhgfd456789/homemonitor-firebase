/**
 * WebStream Precision Control - Pointer Controller
 * Manages active pointer state (single and multi-touch) and coordinates gesture recognition.
 */

import { isTap, buildTapGesture, buildSwipeGesture, buildLongPressGesture, buildDragPathGesture } from './gesture-engine.js';
import { calculatePinch, buildPinchGesture } from './multitouch-engine.js';

export class PointerController {
    constructor(mapPointFunc) {
        this.mapPointFunc = mapPointFunc;
        this.activePointers = new Map();
        this.gesturePoints = [];
        this.mode = 'LEGACY'; // 'LEGACY' or 'PRECISION'
    }

    setMode(newMode) {
        this.mode = newMode === 'PRECISION' ? 'PRECISION' : 'LEGACY';
    }

    handlePointerDown(e) {
        const norm = this.mapPointFunc ? this.mapPointFunc(e.clientX, e.clientY) : null;
        const entry = {
            id: e.pointerId,
            clientX: e.clientX,
            clientY: e.clientY,
            u: norm && norm.valid ? norm.u : null,
            v: norm && norm.valid ? norm.v : null,
            time: Date.now()
        };
        this.activePointers.set(e.pointerId, entry);

        if (this.activePointers.size === 1 && norm && norm.valid) {
            this.gesturePoints = [{ u: norm.u, v: norm.v }];
        }
    }

    handlePointerMove(e) {
        const entry = this.activePointers.get(e.pointerId);
        if (!entry) return null;

        entry.clientX = e.clientX;
        entry.clientY = e.clientY;

        const norm = this.mapPointFunc ? this.mapPointFunc(e.clientX, e.clientY) : null;
        if (norm && norm.valid) {
            entry.u = norm.u;
            entry.v = norm.v;
            if (this.activePointers.size === 1) {
                this.gesturePoints.push({ u: norm.u, v: norm.v });
            }
        }

        if (this.activePointers.size === 2) {
            const pointers = Array.from(this.activePointers.values());
            return calculatePinch(pointers[0], pointers[1], pointers[0], pointers[1]);
        }

        return null;
    }

    handlePointerUp(e, geometryVersion = 1, longPressThresholdMs = 500) {
        const startEntry = this.activePointers.get(e.pointerId);
        this.activePointers.delete(e.pointerId);

        if (!startEntry || startEntry.u === null || startEntry.v === null) {
            this.gesturePoints = [];
            return null;
        }

        const endNorm = this.mapPointFunc ? this.mapPointFunc(e.clientX, e.clientY) : null;
        if (!endNorm || !endNorm.valid) {
            this.gesturePoints = [];
            return null;
        }

        const upEntry = {
            clientX: e.clientX,
            clientY: e.clientY,
            u: endNorm.u,
            v: endNorm.v,
            time: Date.now()
        };

        const duration = upEntry.time - startEntry.time;

        if (isTap(startEntry, upEntry)) {
            if (duration >= longPressThresholdMs) {
                this.gesturePoints = [];
                return buildLongPressGesture(startEntry.u, startEntry.v, duration, geometryVersion);
            }
            this.gesturePoints = [];
            return buildTapGesture(startEntry.u, startEntry.v, geometryVersion);
        }

        const dx = Math.abs(upEntry.clientX - startEntry.clientX);
        const dy = Math.abs(upEntry.clientY - startEntry.clientY);

        if (dx > 5 || dy > 5) {
            if (this.gesturePoints.length > 2) {
                const gesture = buildDragPathGesture(this.gesturePoints, duration, geometryVersion);
                this.gesturePoints = [];
                return gesture;
            }
            this.gesturePoints = [];
            return buildSwipeGesture(startEntry.u, startEntry.v, endNorm.u, endNorm.v, duration, geometryVersion);
        }

        this.gesturePoints = [];
        return null;
    }

    reset() {
        this.activePointers.clear();
        this.gesturePoints = [];
    }
}

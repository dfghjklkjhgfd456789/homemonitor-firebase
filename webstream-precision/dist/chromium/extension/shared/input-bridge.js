/**
 * WebStream Precision Control - Input Bridge (E5)
 * Page-side transport executing authenticated POST requests to /api/input/gesture.
 * Integrates ambient session cookie and X-CSRF-Token.
 */

import { validateOrigin } from './origin-validator.js';

export class InputBridge {
    constructor(customFetch = globalThis.fetch, windowRef = globalThis.window) {
        this.fetch = customFetch;
        this.window = windowRef;
        this.mode = 'LEGACY';
        this.csrfToken = '';
    }

    setMode(newMode) {
        this.mode = newMode === 'PRECISION' ? 'PRECISION' : 'LEGACY';
    }

    setCsrfToken(token) {
        if (typeof token === 'string') {
            this.csrfToken = token.trim();
        }
    }

    async dispatchGesture(gestureRequest) {
        if (!gestureRequest || !gestureRequest.strokes || gestureRequest.strokes.length === 0) {
            return { success: false, reason: 'Invalid gesture request payload' };
        }

        if (typeof this.fetch !== 'function') {
            return { success: false, reason: 'Fetch API unavailable' };
        }

        try {
            const response = await this.fetch('/api/input/gesture', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-Token': this.csrfToken
                },
                body: JSON.stringify(gestureRequest)
            });

            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                return {
                    success: false,
                    status: response.status,
                    reason: data.error || `HTTP error ${response.status}`
                };
            }

            return {
                success: true,
                requestId: data.requestId,
                strokeCount: data.strokeCount
            };
        } catch (e) {
            return { success: false, reason: `Network error: ${e.message}` };
        }
    }
}

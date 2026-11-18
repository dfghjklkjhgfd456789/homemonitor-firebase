/**
 * WebStream Precision Control - Content Script Bootstrap
 */

import { PrecisionController } from './precision-controller.js';
import { getBrowserAPI } from '../shared/browser-api.js';

(function () {
    if (globalThis.__webstream_precision_initialized) return;
    globalThis.__webstream_precision_initialized = true;

    const controller = new PrecisionController(globalThis.window, globalThis.document);
    controller.init();

    const api = getBrowserAPI();
    if (api && api.runtime && api.runtime.onMessage) {
        api.runtime.onMessage.addListener((message, sender, sendResponse) => {
            controller.handleMessage(message, sendResponse);
            return true;
        });
    }

    globalThis.__webstream_precision_controller = controller;
})();

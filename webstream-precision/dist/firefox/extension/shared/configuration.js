/**
 * WebStream Precision Control - Configuration Manager
 */

import { STORAGE_KEYS, DEFAULT_SETTINGS } from './constants.js';
import { BrowserAPIWrapper } from './browser-api.js';
import { validateOrigin } from './origin-validator.js';

export class ConfigurationManager {
    constructor(browserAPI = new BrowserAPIWrapper()) {
        this.browserAPI = browserAPI;
    }

    async loadSettings() {
        const stored = await this.browserAPI.getStorage([
            STORAGE_KEYS.APPROVED_ORIGINS,
            STORAGE_KEYS.OVERLAY_ENABLED,
            STORAGE_KEYS.DIAGNOSTICS_ENABLED
        ]);

        const approvedOrigins = Array.isArray(stored[STORAGE_KEYS.APPROVED_ORIGINS])
            ? stored[STORAGE_KEYS.APPROVED_ORIGINS]
            : [...DEFAULT_SETTINGS.approvedOrigins];

        const overlayEnabled = typeof stored[STORAGE_KEYS.OVERLAY_ENABLED] === 'boolean'
            ? stored[STORAGE_KEYS.OVERLAY_ENABLED]
            : DEFAULT_SETTINGS.overlayEnabled;

        const diagnosticsEnabled = typeof stored[STORAGE_KEYS.DIAGNOSTICS_ENABLED] === 'boolean'
            ? stored[STORAGE_KEYS.DIAGNOSTICS_ENABLED]
            : DEFAULT_SETTINGS.diagnosticsEnabled;

        return {
            approvedOrigins,
            overlayEnabled,
            diagnosticsEnabled
        };
    }

    async saveSettings(settings) {
        const sanitizedOrigins = Array.isArray(settings.approvedOrigins)
            ? settings.approvedOrigins.filter(o => validateOrigin(o).valid)
            : [...DEFAULT_SETTINGS.approvedOrigins];

        const items = {
            [STORAGE_KEYS.APPROVED_ORIGINS]: sanitizedOrigins,
            [STORAGE_KEYS.OVERLAY_ENABLED]: Boolean(settings.overlayEnabled),
            [STORAGE_KEYS.DIAGNOSTICS_ENABLED]: Boolean(settings.diagnosticsEnabled)
        };

        return this.browserAPI.setStorage(items);
    }

    async addApprovedOrigin(originString) {
        const val = validateOrigin(originString);
        if (!val.valid) {
            return { success: false, reason: val.reason };
        }

        const current = await this.loadSettings();
        if (!current.approvedOrigins.includes(val.normalizedOrigin)) {
            current.approvedOrigins.push(val.normalizedOrigin);
            await this.saveSettings(current);
        }

        return { success: true, normalizedOrigin: val.normalizedOrigin };
    }

    async removeApprovedOrigin(originString) {
        const current = await this.loadSettings();
        const initialLen = current.approvedOrigins.length;
        current.approvedOrigins = current.approvedOrigins.filter(o => o !== originString.trim().toLowerCase());

        if (current.approvedOrigins.length !== initialLen) {
            await this.saveSettings(current);
            return { success: true };
        }
        return { success: false, reason: 'Origin not found' };
    }
}

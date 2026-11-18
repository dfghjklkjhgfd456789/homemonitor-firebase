/**
 * WebStream Precision Control - Browser API Abstraction Layer
 * Cross-browser compatibility layer for Chromium (chrome.*) and Firefox (browser.*)
 */

export const getBrowserAPI = () => {
    if (typeof globalThis.browser !== 'undefined' && globalThis.browser.runtime) {
        return globalThis.browser;
    }
    if (typeof globalThis.chrome !== 'undefined' && globalThis.chrome.runtime) {
        return globalThis.chrome;
    }
    return null;
};

export class BrowserAPIWrapper {
    constructor(api = getBrowserAPI()) {
        this.api = api;
    }

    isAvailable() {
        return this.api !== null;
    }

    async getStorage(keys) {
        if (!this.api || !this.api.storage || !this.api.storage.local) {
            return {};
        }
        return new Promise((resolve) => {
            if (typeof this.api.storage.local.get.length === 'function') {
                this.api.storage.local.get(keys).then(resolve).catch(() => resolve({}));
            } else {
                this.api.storage.local.get(keys, (result) => resolve(result || {}));
            }
        });
    }

    async setStorage(items) {
        if (!this.api || !this.api.storage || !this.api.storage.local) {
            return false;
        }
        return new Promise((resolve) => {
            try {
                const res = this.api.storage.local.set(items);
                if (res && typeof res.then === 'function') {
                    res.then(() => resolve(true)).catch(() => resolve(false));
                } else {
                    this.api.storage.local.set(items, () => resolve(true));
                }
            } catch (_) {
                resolve(false);
            }
        });
    }

    async getActiveTab() {
        if (!this.api || !this.api.tabs || !this.api.tabs.query) {
            return null;
        }
        return new Promise((resolve) => {
            try {
                const queryInfo = { active: true, currentWindow: true };
                const res = this.api.tabs.query(queryInfo);
                if (res && typeof res.then === 'function') {
                    res.then((tabs) => resolve(tabs[0] || null)).catch(() => resolve(null));
                } else {
                    this.api.tabs.query(queryInfo, (tabs) => resolve(tabs[0] || null));
                }
            } catch (_) {
                resolve(null);
            }
        });
    }

    async executeScript(tabId, scriptDetails) {
        if (!this.api || !this.api.scripting || !this.api.scripting.executeScript) {
            return null;
        }
        return new Promise((resolve) => {
            try {
                const res = this.api.scripting.executeScript({
                    target: { tabId },
                    ...scriptDetails
                });
                if (res && typeof res.then === 'function') {
                    res.then(resolve).catch(() => resolve(null));
                } else {
                    resolve(null);
                }
            } catch (_) {
                resolve(null);
            }
        });
    }

    async sendMessageToTab(tabId, message) {
        if (!this.api || !this.api.tabs || !this.api.tabs.sendMessage) {
            return null;
        }
        return new Promise((resolve) => {
            try {
                const res = this.api.tabs.sendMessage(tabId, message);
                if (res && typeof res.then === 'function') {
                    res.then(resolve).catch(() => resolve(null));
                } else {
                    this.api.tabs.sendMessage(tabId, message, (response) => resolve(response || null));
                }
            } catch (_) {
                resolve(null);
            }
        });
    }
}

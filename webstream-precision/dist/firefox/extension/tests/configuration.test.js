import test from 'node:test';
import assert from 'node:assert/strict';
import { ConfigurationManager } from '../shared/configuration.js';
import { DEFAULT_SETTINGS, STORAGE_KEYS } from '../shared/constants.js';

class MockBrowserAPI {
    constructor(store = {}) {
        this.store = store;
    }
    async getStorage(keys) {
        const res = {};
        keys.forEach(k => { if (k in this.store) res[k] = this.store[k]; });
        return res;
    }
    async setStorage(items) {
        Object.assign(this.store, items);
        return true;
    }
}

test('Configuration - Loads default settings when storage empty', async () => {
    const mockAPI = new MockBrowserAPI();
    const config = new ConfigurationManager(mockAPI);
    const settings = await config.loadSettings();
    assert.deepEqual(settings.approvedOrigins, DEFAULT_SETTINGS.approvedOrigins);
    assert.equal(settings.overlayEnabled, true);
    assert.equal(settings.diagnosticsEnabled, true);
});

test('Configuration - Adds valid approved origin and rejects invalid origin', async () => {
    const mockAPI = new MockBrowserAPI();
    const config = new ConfigurationManager(mockAPI);

    const validRes = await config.addApprovedOrigin('https://valid-tunnel.trycloudflare.com');
    assert.equal(validRes.success, true);

    const settings = await config.loadSettings();
    assert.ok(settings.approvedOrigins.includes('https://valid-tunnel.trycloudflare.com'));

    const invalidRes = await config.addApprovedOrigin('https://eviltrycloudflare.com');
    assert.equal(invalidRes.success, false);
});

test('Configuration - Ensures zero credentials/tokens stored', async () => {
    const mockAPI = new MockBrowserAPI();
    const config = new ConfigurationManager(mockAPI);
    await config.saveSettings({
        approvedOrigins: DEFAULT_SETTINGS.approvedOrigins,
        overlayEnabled: true,
        diagnosticsEnabled: true
    });

    const storedKeys = Object.keys(mockAPI.store);
    assert.ok(!storedKeys.includes('session_id'));
    assert.ok(!storedKeys.includes('csrfToken'));
    assert.ok(!storedKeys.includes('passcode'));
});

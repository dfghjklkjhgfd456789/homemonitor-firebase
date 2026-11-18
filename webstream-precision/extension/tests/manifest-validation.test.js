import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const chromiumManifestPath = path.join(process.cwd(), 'manifests/chromium/manifest.json');
const firefoxManifestPath = path.join(process.cwd(), 'manifests/firefox/manifest.json');

test('Manifest Validation - Chromium Manifest V3 Structure', () => {
    assert.ok(fs.existsSync(chromiumManifestPath), 'Chromium manifest file must exist');
    const content = JSON.parse(fs.readFileSync(chromiumManifestPath, 'utf8'));

    assert.equal(content.manifest_version, 3);
    assert.equal(content.name, 'WebStream Precision Control');
    assert.ok(content.permissions.includes('activeTab'));
    assert.ok(content.permissions.includes('scripting'));
    assert.ok(content.permissions.includes('storage'));

    // Reject blanket wildcard host permissions
    assert.ok(!content.permissions.includes('<all_urls>'));
    assert.ok(!content.host_permissions || !content.host_permissions.includes('<all_urls>'));
});

test('Manifest Validation - Firefox Manifest V3 Structure', () => {
    assert.ok(fs.existsSync(firefoxManifestPath), 'Firefox manifest file must exist');
    const content = JSON.parse(fs.readFileSync(firefoxManifestPath, 'utf8'));

    assert.equal(content.manifest_version, 3);
    assert.equal(content.name, 'WebStream Precision Control');
    assert.ok(content.browser_specific_settings && content.browser_specific_settings.gecko);
});
